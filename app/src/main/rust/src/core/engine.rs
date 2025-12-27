//! Main engine - public API for Android Enhancer.
//!
//! The Engine coordinates sensing, mode switching, and tuning.
//! Tokio runtime is initialized first, then all components use async I/O.

use crate::core::mode::Mode;
use crate::core::runner::{Runner, TOUCH_BOOST_ENABLED};
use crate::sense::{init_profile, profile, LoadSensor, TouchSensor};
use crate::util::log::{self, Log};
use crate::util::sysfs;
use std::collections::HashMap;
use std::sync::atomic::Ordering;
use std::sync::Arc;
use tokio::runtime::Runtime;
use std::path::PathBuf;

/// Main controller for Android Enhancer.
pub struct Engine {
    running: bool,
    mode: Mode,
    app_modes: HashMap<String, Mode>,
    current_app: Option<String>,
    screen_on: bool,
    battery_level: i32,

    log: Option<Arc<Log>>,

    load_sensor: Option<LoadSensor>,
    touch_sensor: Option<TouchSensor>,
    runner: Option<Runner>,

    runtime: Option<Runtime>,
}

impl Engine {
    pub fn new() -> Self {
        Self {
            running: false,
            mode: Mode::Balanced,
            app_modes: HashMap::new(),
            current_app: None,
            screen_on: true,
            battery_level: 100,
            log: None,
            load_sensor: None,
            touch_sensor: None,
            runner: None,
            runtime: None,
        }
    }

    pub fn start(&mut self, log_path: Option<String>, sysfs_backup_path: Option<String>) -> bool {
        if !self.running {
            // Create tokio runtime first
            let rt = tokio::runtime::Builder::new_multi_thread()
                .worker_threads(2)
                .thread_name("ae_async")
                .enable_all()
                .build()
                .expect("can't create tokio runtime");

            // Store runtime handle for async logging from sync contexts
            log::set_runtime_handle(rt.handle().clone());

            // Initialize device profile async
            rt.block_on(init_profile());

            // Initialize sysfs backup - restore from disk if previous session didn't stop cleanly
            rt.block_on(sysfs::init_backup(sysfs_backup_path.as_deref()));

            // Create log and initialize file using the runtime
            let log = Log::new(log_path.as_deref());
            rt.block_on(log.init_file());
            let log = Arc::new(log);
            self.log = Some(Arc::clone(&log));

            // Start sensors asynchronously
            let (load_sensor, touch_sensor) = rt.block_on(async {
                let ls = LoadSensor::start().await;
                let ts = TouchSensor::start(&log).await;
                (ls, ts)
            });

            self.load_sensor = Some(load_sensor);
            self.touch_sensor = Some(touch_sensor);

            self.runtime = Some(rt);
            self.log().say("Android Enhancer is running");
            self.running = true;
        } else if let Some(path) = log_path
            && let (Some(log), Some(rt)) = (&self.log, &self.runtime)
        {
            rt.block_on(log.set_path(Some(PathBuf::from(path))));
        }

        true
    }

    pub fn stop(&mut self) {
        if !self.running {
            return;
        }
        self.running = false;

        if let Some(s) = self.touch_sensor.take() {
            s.stop();
        }
        if let Some(s) = self.load_sensor.take() {
            s.stop();
        }

        if let Some(rt) = &self.runtime {
            // Await runner completion before restoring
            if let Some(r) = self.runner.take() {
                rt.block_on(r.stop());
            }

            let count = rt.block_on(sysfs::restore_all());
            self.log().say(&format!("Restored {} system values", count));
        }

        self.log().say("Android Enhancer stopped");
    }

    pub fn set_mode(&mut self, mode: Mode, on_change: impl Fn(u32) + Send + 'static) {
        if !self.running {
            return;
        }

        if self.mode == mode && self.runner.is_some() {
            return;
        }

        self.mode = mode;

        if let Some(r) = self.runner.take()
            && let Some(rt) = &self.runtime
        {
            rt.block_on(r.stop());
        }

        self.ensure_sensors();

        let rt = self.runtime.as_ref().unwrap();
        let log = Arc::clone(self.log.as_ref().unwrap());
        let load_rx = self.load_sensor.as_mut().unwrap().take_receiver();
        let touch_rx = self.touch_sensor.as_mut().unwrap().take_receiver();
        let apps = self.app_modes.clone();
        let current = self.current_app.clone();
        let screen_on = self.screen_on;
        let battery_level = self.battery_level;

        let runner = rt.block_on(async {
            Runner::start(
                log,
                load_rx,
                touch_rx,
                mode,
                apps,
                current,
                screen_on,
                battery_level,
                on_change,
            )
            .await
        });

        self.runner = Some(runner);
    }

    pub fn set_app_mode(&mut self, pkg: String, mode: Mode) {
        if !self.running {
            return;
        }
        if mode == Mode::Auto {
            self.app_modes.remove(&pkg);
        } else {
            self.app_modes.insert(pkg.clone(), mode);
        }
        if let (Some(r), Some(rt)) = (&self.runner, &self.runtime) {
            let apps = self.app_modes.clone();
            rt.block_on(r.update_apps(apps));
        }
        if self.current_app.as_ref() == Some(&pkg) {
            self.push_app(Some(pkg));
        }
    }

    pub fn remove_app_mode(&mut self, pkg: String) {
        if !self.running {
            return;
        }
        self.app_modes.remove(&pkg);
        if let (Some(r), Some(rt)) = (&self.runner, &self.runtime) {
            let apps = self.app_modes.clone();
            rt.block_on(r.update_apps(apps));
        }
        if self.current_app.as_ref() == Some(&pkg) {
            self.push_app(Some(pkg));
        }
    }

    pub fn push_app(&mut self, pkg: Option<String>) {
        if !self.running || !self.mode.is_auto() {
            return;
        }

        let changed = self.current_app.as_ref() != pkg.as_ref();
        self.current_app = pkg.clone();

        if changed {
            let name = self.current_app.as_deref().unwrap_or("home");
            self.log().say(&format!("Now in {name}"));
            if let (Some(r), Some(rt)) = (&self.runner, &self.runtime) {
                let mode = self
                    .app_modes
                    .get(self.current_app.as_ref().unwrap_or(&String::new()))
                    .copied();
                let pkg_clone = pkg.clone();
                rt.block_on(r.update_app(pkg_clone, mode));
            }
        }
    }

    pub fn set_screen_state(&mut self, is_on: bool) {
        if !self.running {
            return;
        }
        let changed = self.screen_on != is_on;
        self.screen_on = is_on;

        if changed {
            self.log()
                .say(&format!("Screen {}", if is_on { "on" } else { "off" }));
            if let (Some(r), Some(rt)) = (&self.runner, &self.runtime) {
                rt.block_on(r.update_screen(is_on));
            }
        }
    }

    pub fn set_battery_level(&mut self, level: i32) {
        if !self.running {
            return;
        }
        let threshold = profile().low_battery_threshold();
        let was_low = self.battery_level <= threshold;
        let is_low = level <= threshold;
        self.battery_level = level;

        if was_low != is_low {
            if is_low {
                self.log().say(&format!(
                    "Battery low ({}%) — forcing Powersaver in Auto mode",
                    level
                ));
            } else {
                self.log().say(&format!("Battery recovered ({}%)", level));
            }
        }

        if let (Some(r), Some(rt)) = (&self.runner, &self.runtime) {
            rt.block_on(r.update_battery(level));
        }
    }

    pub fn clear_log(&self) {
        if let Some(log) = &self.log {
            log.clear();
        }
    }

    pub fn is_running(&self) -> bool {
        self.running
    }

    pub fn set_touch_boost_enabled(&self, enabled: bool) {
        let prev = TOUCH_BOOST_ENABLED.swap(enabled, Ordering::Relaxed);
        if prev != enabled
            && let Some(log) = &self.log
        {
            log.say(&format!(
                "Touch boost {}",
                if enabled { "enabled" } else { "disabled" }
            ));
        }
    }

    pub fn is_touch_boost_enabled(&self) -> bool {
        TOUCH_BOOST_ENABLED.load(Ordering::Relaxed)
    }

    fn log(&self) -> &Log {
        self.log.as_ref().expect("not started")
    }

    fn ensure_sensors(&mut self) {
        if let (Some(rt), Some(log)) = (&self.runtime, &self.log) {
            // Recreate sensors if they don't exist or if their receiver was already taken
            if self.load_sensor.as_ref().is_none_or(|s| !s.has_receiver()) {
                self.load_sensor = Some(rt.block_on(LoadSensor::start()));
            }
            if self.touch_sensor.as_ref().is_none_or(|s| !s.has_receiver()) {
                self.touch_sensor = Some(rt.block_on(TouchSensor::start(log)));
            }
        }
    }
}

impl Default for Engine {
    fn default() -> Self {
        Self::new()
    }
}

impl Drop for Engine {
    fn drop(&mut self) {
        if self.running {
            if let Some(s) = self.touch_sensor.take() {
                s.stop();
            }
            if let Some(s) = self.load_sensor.take() {
                s.stop();
            }

            if let Some(rt) = &self.runtime {
                // Await runner completion before restoring
                if let Some(r) = self.runner.take() {
                    rt.block_on(r.stop());
                }

                let count = rt.block_on(sysfs::restore_all());
                if let Some(log) = &self.log {
                    log.say(&format!("Restored {} system values on drop", count));
                }
            }
        }
    }
}
