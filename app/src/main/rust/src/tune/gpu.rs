//! GPU tuning - governor, frequency, power policy.

use super::paths;
use crate::core::mode::Mode;
use crate::util::sysfs;

pub async fn apply(mode: Mode) {
    tokio::join!(
        tune_governor(mode),
        tune_params(mode),
        set_floor_ratio(mode),
    );
}

// --- Formulas ---

fn gpu_floor_ratio(mode: Mode) -> f64 {
    let i = mode.intensity();
    0.15 + i * 0.65
}

fn gpu_highspeed_load(mode: Mode) -> &'static str {
    let i = mode.intensity();
    if i > 0.9 { "70" }
    else if i > 0.5 { "80" }
    else if i > 0.2 { "90" }
    else { "99" }
}

fn gpu_governor(available: &str, mode: Mode) -> &'static str {
    let i = mode.intensity();
    if i > 0.9 && available.contains("performance") {
        "performance"
    } else if available.contains("simple_ondemand") {
        "simple_ondemand"
    } else if available.contains("ondemand") {
        "ondemand"
    } else {
        ""
    }
}

// --- Tuning Functions ---

async fn tune_governor(mode: Mode) {
    let gov_paths = [
        (format!("{}/devfreq/governor", paths::GPU_KGSL), format!("{}/devfreq/available_governors", paths::GPU_KGSL)),
        (format!("{}/gpu_governor", paths::GPU_KERNEL), format!("{}/available_governors", paths::GPU_KERNEL)),
    ];

    for (gpath, avail_path) in &gov_paths {
        if let Some(avail) = sysfs::read(avail_path).await {
            let gov = gpu_governor(&avail, mode);
            if !gov.is_empty() {
                sysfs::write(gpath, gov).await;
            }
        }
    }
}

async fn tune_params(mode: Mode) {
    if !sysfs::exists(paths::GPU_KGSL).await {
        return;
    }

    let i = mode.intensity();
    let hispeed = gpu_highspeed_load(mode);
    let throttle = if i < 0.2 { "1" } else { "0" };
    let dvfs = if i > 0.9 { "0" } else { "1" };
    let policy = if i < 0.2 { "coarse_demand" } else { "always_on" };
    let max_freq = sysfs::read(&format!("{}/devfreq/max_freq", paths::GPU_KGSL)).await.unwrap_or_default();

    sysfs::write(&format!("{}/throttling", paths::GPU_KGSL), throttle).await;
    sysfs::write(&format!("{}/thermal_pwrlevel", paths::GPU_KGSL), "0").await;
    sysfs::write(&format!("{}/highspeed_load", paths::GPU_KGSL), hispeed).await;
    sysfs::write(&format!("{}/highspeed_clock", paths::GPU_KGSL), &max_freq).await;
    sysfs::write(&format!("{}/dvfs", paths::GPU_KGSL), dvfs).await;
    sysfs::write(&format!("{}/power_policy", paths::GPU_KGSL), policy).await;
}

async fn set_floor_ratio(mode: Mode) {
    let ratio = gpu_floor_ratio(mode);
    for (max_path, min_path) in paths::GPU_FREQ_PAIRS {
        if let Some(max) = sysfs::read_i64(max_path).await.filter(|&m| m > 0) {
            let floor = ((max as f64 * ratio) as i64).max(100_000);
            sysfs::write(min_path, &floor.to_string()).await;
        }
    }
}

pub async fn boost(strength: f64) {
    let ratio = (0.30 + strength * 0.55).clamp(0.25, 0.90);
    for (max_path, min_path) in paths::GPU_FREQ_PAIRS {
        if let Some(max) = sysfs::read_i64(max_path).await.filter(|&m| m > 0) {
            let floor = ((max as f64 * ratio) as i64).max(100_000);
            sysfs::write(min_path, &floor.to_string()).await;
        }
    }
}
