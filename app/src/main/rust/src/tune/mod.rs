//! System tuning - applies kernel parameters.
//!
//! Rate-limited to prevent kernel overload from rapid mode switches.

mod cpu;
mod debug;
mod fs;
mod gpu;
mod io;
mod memory;
mod network;
pub mod paths;
mod power;
pub mod scheduler;
mod thermal;

use crate::core::mode::{BoostType, Mode};
use crate::util::log::Log;
use crate::util::sysfs;
use once_cell::sync::Lazy;
use parking_lot::Mutex;
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::{Duration, Instant};

/// Minimum interval between full mode applications (prevents write storms)
const MIN_APPLY_INTERVAL_MS: u64 = 500;

/// Minimum interval between touch boosts (shorter for responsiveness)
const MIN_BOOST_INTERVAL_MS: u64 = 100;

/// Last mode application time (rate limiting)
static LAST_APPLY: Lazy<Mutex<Instant>> = Lazy::new(|| Mutex::new(Instant::now() - Duration::from_secs(10)));

/// Last touch boost time (rate limiting)
static LAST_BOOST: Lazy<Mutex<Instant>> = Lazy::new(|| Mutex::new(Instant::now() - Duration::from_secs(10)));

/// First-time initialization flag - dangerous writes only on first apply
static FIRST_APPLY: AtomicBool = AtomicBool::new(true);

/// Check and consume first-apply flag
pub fn take_first_apply() -> bool {
    FIRST_APPLY.swap(false, Ordering::Relaxed)
}

/// Reset first-apply flag to allow first-apply behavior on next start.
pub fn reset_first_apply() {
    FIRST_APPLY.store(true, Ordering::Relaxed);
}

/// Apply all settings for a mode.
/// Rate-limited to prevent kernel overload from rapid switches.
/// Returns true if applied, false if skipped due to rate limiting.
pub async fn apply_mode(mode: Mode, log: &Log) -> bool {
    // Rate limit: skip if applied too recently
    {
        let mut last = LAST_APPLY.lock();
        if last.elapsed().as_millis() < MIN_APPLY_INTERVAL_MS as u128 {
            return false;
        }
        *last = Instant::now();
    }

    let is_first = take_first_apply();

    log.say(&format!("Switching to {} — {}", mode.name(), mode.describe()));

    // Phase 1: Power management first (ensures all cores are online before configuring them)
    power::apply(mode).await;

    // Phase 2: All other subsystems concurrently
    tokio::join!(
        scheduler::apply(mode, is_first),
        cpu::apply(mode),
        gpu::apply(mode),
        memory::apply(mode, is_first),
        io::apply(mode),
        network::apply(mode),
        thermal::apply(mode),
        fs::apply(mode),
        debug::apply(),
    );

    // Sync backup to disk only on first apply (original kernel values are captured during first writes)
    if is_first {
        sysfs::sync_backup().await;
    }

    true
}

/// Intelligent touch boost - only boosts what's needed based on current workload.
/// Rate-limited to prevent sysfs write storms during rapid touch interactions.
pub async fn boost_touch(strength: f64, boost_type: BoostType) -> &'static str {
    // Rate limit: skip if boosted too recently
    {
        let mut last = LAST_BOOST.lock();
        if last.elapsed().as_millis() < MIN_BOOST_INTERVAL_MS as u128 {
            return "skipped";
        }
        *last = Instant::now();
    }

    match boost_type {
        BoostType::CpuOnly => {
            tokio::join!(cpu::boost(strength), io::boost(strength));
            "CPU"
        }
        BoostType::GpuOnly => {
            gpu::boost(strength).await;
            "GPU"
        }
        BoostType::Both => {
            tokio::join!(cpu::boost(strength), gpu::boost(strength), io::boost(strength));
            "CPU+GPU"
        }
        BoostType::None => "none",
    }
}
