//! Background runner loop for mode management.
//!
//! Handles load-based mode switching, touch boost, screen/battery state.

use crate::core::mode::{BoostType, Mode, Touch};
use crate::core::tracker::Tracker;
use crate::sense::{profile, Load};
use crate::tune;
use crate::util::log::Log;
use std::collections::HashMap;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::{Duration, Instant};
use tokio::sync::mpsc;
use tokio_util::sync::CancellationToken;

/// Get dynamic low battery threshold from device info
fn low_battery_threshold() -> i32 {
    profile().low_battery_threshold()
}

/// Global touch boost flag
pub static TOUCH_BOOST_ENABLED: AtomicBool = AtomicBool::new(true);

/// Commands sent to the runner
pub enum Cmd {
    Apps(HashMap<String, Mode>),
    App(Option<String>, Option<Mode>),
    Screen(bool),
    Battery(i32),
}

/// Background runner that manages mode switching.
pub struct Runner {
    cmd_tx: mpsc::Sender<Cmd>,
    cancel: CancellationToken,
    handle: tokio::task::JoinHandle<()>,
}

impl Runner {
    #[allow(clippy::too_many_arguments)]
    pub async fn start(
        log: Arc<Log>,
        load_rx: mpsc::Receiver<Load>,
        touch_rx: mpsc::Receiver<Touch>,
        mode: Mode,
        apps: HashMap<String, Mode>,
        current: Option<String>,
        screen_on: bool,
        battery_level: i32,
        on_change: impl Fn(u32) + Send + 'static,
    ) -> Self {
        let (cmd_tx, cmd_rx) = mpsc::channel(32);
        let cancel = CancellationToken::new();
        let cancel_clone = cancel.clone();

        let handle = tokio::spawn(async move {
            run_loop(
                cancel_clone,
                cmd_rx,
                load_rx,
                touch_rx,
                log,
                mode,
                apps,
                current,
                screen_on,
                battery_level,
                on_change,
            )
            .await;
        });

        Self { cmd_tx, cancel, handle }
    }

    pub async fn update_apps(&self, apps: HashMap<String, Mode>) {
        let _ = self.cmd_tx.send(Cmd::Apps(apps)).await;
    }

    pub async fn update_app(&self, pkg: Option<String>, mode: Option<Mode>) {
        let _ = self.cmd_tx.send(Cmd::App(pkg, mode)).await;
    }

    pub async fn update_screen(&self, is_on: bool) {
        let _ = self.cmd_tx.send(Cmd::Screen(is_on)).await;
    }

    pub async fn update_battery(&self, level: i32) {
        let _ = self.cmd_tx.send(Cmd::Battery(level)).await;
    }

    /// Stop the runner. Cancels the task and awaits its completion.
    pub async fn stop(self) {
        self.cancel.cancel();
        let _ = self.handle.await;
    }
}

#[allow(clippy::too_many_arguments)]
async fn run_loop(
    cancel: CancellationToken,
    mut cmd_rx: mpsc::Receiver<Cmd>,
    mut load_rx: mpsc::Receiver<Load>,
    mut touch_rx: mpsc::Receiver<Touch>,
    log: Arc<Log>,
    base_mode: Mode,
    mut app_modes: HashMap<String, Mode>,
    mut current_app: Option<String>,
    initial_screen_on: bool,
    initial_battery_level: i32,
    on_change: impl Fn(u32),
) {
    let mut tracker = Tracker::new();
    let mut active_mode = if base_mode.is_auto() { Mode::Balanced } else { base_mode };
    let mut app_override: Option<Mode> = None;
    let mut touch_logged = false;
    let mut last_touch_log = Instant::now();
    let mut last_switch = Instant::now();
    let mut stable = 0usize;
    let mut current_load = Load::default();

    let mut screen_on = initial_screen_on;
    let mut battery_level = initial_battery_level;
    let mut forced_powersaver = base_mode.is_auto() && (!screen_on || battery_level <= low_battery_threshold());

    if forced_powersaver && base_mode.is_auto() {
        active_mode = Mode::Powersaver;
        if !screen_on {
            log.say("Starting with screen off → Powersaver");
        } else {
            log.say(&format!("Starting with low battery ({}%) → Powersaver", battery_level));
        }
    }

    tune::apply_mode(active_mode, &log).await;
    on_change(active_mode.code());

    loop {
        tokio::select! {
            _ = cancel.cancelled() => break,

            Some(cmd) = cmd_rx.recv() => {
                match cmd {
                    Cmd::Apps(a) => app_modes = a,
                    Cmd::App(pkg, mode) => {
                        let new_override = mode.filter(|m| *m != Mode::Auto);
                        current_app = pkg.clone();

                        if let Some(target) = new_override {
                            if target != active_mode
                                && last_switch.elapsed().as_millis() >= 200
                                && tune::apply_mode(target, &log).await
                            {
                                on_change(target.code());
                                if let Some(ref app) = pkg {
                                    log.say(&format!("Switched to {} for {} (per-app override)", target.name(), app));
                                }
                                active_mode = target;
                                last_switch = Instant::now();
                                stable = 0;
                                forced_powersaver = false;
                            }
                        } else if app_override.is_some() && new_override.is_none() {
                            stable = 0;
                        }
                        app_override = new_override;
                    }
                    Cmd::Screen(is_on) => {
                        let was_off = !screen_on;
                        screen_on = is_on;

                        if base_mode.is_auto() && app_override.is_none() {
                            if !is_on && active_mode != Mode::Powersaver {
                                if tune::apply_mode(Mode::Powersaver, &log).await {
                                    on_change(Mode::Powersaver.code());
                                    log.say("Screen off → switching to Powersaver");
                                    active_mode = Mode::Powersaver;
                                    last_switch = Instant::now();
                                    forced_powersaver = true;
                                }
                            } else if was_off && forced_powersaver {
                                tune::boost_touch(0.8, BoostType::Both).await;
                                if tune::apply_mode(Mode::Balanced, &log).await {
                                    on_change(Mode::Balanced.code());
                                    log.say("Screen on → switching to Balanced");
                                    active_mode = Mode::Balanced;
                                    last_switch = Instant::now();
                                    forced_powersaver = false;
                                    stable = 0;
                                }
                            }
                        }
                    }
                    Cmd::Battery(level) => {
                        let was_low = battery_level <= low_battery_threshold();
                        let is_low = level <= low_battery_threshold();
                        battery_level = level;

                        if base_mode.is_auto() && app_override.is_none() {
                            if is_low && !was_low && active_mode != Mode::Powersaver {
                                if tune::apply_mode(Mode::Powersaver, &log).await {
                                    on_change(Mode::Powersaver.code());
                                    log.say(&format!("Battery low ({}%) → switching to Powersaver", level));
                                    active_mode = Mode::Powersaver;
                                    last_switch = Instant::now();
                                    forced_powersaver = true;
                                }
                            } else if !is_low && was_low && forced_powersaver && screen_on {
                                let recovery_mode = tracker.suggest();
                                if tune::apply_mode(recovery_mode, &log).await {
                                    on_change(recovery_mode.code());
                                    log.say(&format!("Battery recovered ({}%) → switching to {}", level, recovery_mode.name()));
                                    active_mode = recovery_mode;
                                    last_switch = Instant::now();
                                    forced_powersaver = false;
                                    stable = 0;
                                }
                            }
                        }
                    }
                }
            }

            Some(load) = load_rx.recv() => {
                tracker.observe(&load);
                current_load = load;

                if forced_powersaver { continue; }

                let mut target = if base_mode.is_auto() {
                    tracker.suggest()
                } else {
                    base_mode
                };

                if let Some(pkg) = &current_app
                    && let Some(m) = app_modes.get(pkg).filter(|m| **m != Mode::Auto)
                {
                    target = *m;
                }
                if let Some(m) = app_override {
                    target = m;
                }

                if target != active_mode {
                    stable += 1;
                } else {
                    stable = 0;
                }

                let intensity = active_mode.intensity();
                let threshold = tracker.stability_threshold(intensity, target as u32 > active_mode as u32);
                let min_interval = tracker.mode_switch_interval_ms(intensity);
                let time_ok = last_switch.elapsed().as_millis() >= min_interval as u128;

                if stable >= threshold
                    && time_ok
                    && tune::apply_mode(target, &log).await
                {
                    on_change(target.code());
                    log_switch(&log, target, &current_load);
                    active_mode = target;
                    last_switch = Instant::now();
                    stable = 0;
                }
            }

            Some(touch) = touch_rx.recv() => {
                tracker.touch(touch);

                if !TOUCH_BOOST_ENABLED.load(Ordering::Relaxed) { continue; }

                let strength = active_mode.intensity() + touch.boost() * 0.4;
                let boost_type = determine_boost_type(&current_load);

                if boost_type != BoostType::None {
                    let boosted = tune::boost_touch(strength.clamp(0.0, 1.0), boost_type).await;

                    if boosted != "skipped" && (!touch_logged || last_touch_log.elapsed().as_secs() >= 3) {
                        let boost_pct = (touch.boost() * 100.0) as u32;
                        log.say(&format!("{} detected → {} boost +{}%", touch.name(), boosted, boost_pct));
                        last_touch_log = Instant::now();
                        touch_logged = true;
                    }
                }
            }

            // Timeout fallback to prevent indefinite blocking
            _ = tokio::time::sleep(Duration::from_millis(500)) => {
                // Periodic wake-up to check cancellation
            }
        }
    }

    log.say(&format!("{} mode stopped", base_mode.name()));
}

fn log_switch(log: &Log, mode: Mode, load: &Load) {
    let why = if load.gpu > 0.75 {
        "GPU working hard"
    } else if load.cpu > 0.75 {
        "CPU busy"
    } else if load.cpu < 0.15 && load.gpu < 0.15 {
        "system idle"
    } else {
        "workload changed"
    };
    log.say(&format!("Now {} — {} (CPU {:.0}%, GPU {:.0}%)", mode.name(), why, load.cpu * 100.0, load.gpu * 100.0));
}

fn determine_boost_type(load: &Load) -> BoostType {
    const CPU_LOW: f64 = 0.25;
    const CPU_HIGH: f64 = 0.60;
    const GPU_LOW: f64 = 0.20;
    const GPU_HIGH: f64 = 0.50;

    let cpu_needs_boost = load.cpu < CPU_HIGH;
    let gpu_needs_boost = load.gpu < GPU_HIGH;
    let cpu_is_idle = load.cpu < CPU_LOW;
    let gpu_is_idle = load.gpu < GPU_LOW;

    if !cpu_needs_boost && !gpu_needs_boost {
        return BoostType::None;
    }

    match (cpu_is_idle, gpu_is_idle, cpu_needs_boost, gpu_needs_boost) {
        (true, false, true, _) => BoostType::CpuOnly,
        (false, true, _, true) => BoostType::GpuOnly,
        (_, _, true, true) => BoostType::Both,
        (_, _, true, false) => BoostType::CpuOnly,
        (_, _, false, true) => BoostType::GpuOnly,
        _ => BoostType::Both,
    }
}
