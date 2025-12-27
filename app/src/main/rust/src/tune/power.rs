//! Power management tuning.

use super::paths;
use crate::core::mode::Mode;
use crate::util::sysfs;

pub async fn apply(mode: Mode) {
    tokio::join!(
        cores_online(),
        core_ctl(mode),
        hotplug(mode),
        pewq(mode),
        lpm(mode),
        devfreq_boost(mode),
        battery_saver(mode),
    );
}

async fn cores_online() {
    for path in sysfs::glob(&format!("{}/cpu*/online", paths::CPU)).await {
        sysfs::write(&path.to_string_lossy(), "1").await;
    }
}

async fn core_ctl(mode: Mode) {
    let i = mode.intensity();
    let enable = if i > 0.5 { "0" } else { "1" };
    let disable = if i > 0.5 { "1" } else { "0" };

    for path in sysfs::glob(&format!("{}/cpu*/core_ctl", paths::CPU)).await {
        let base = path.display().to_string();
        sysfs::write(&format!("{base}/enable"), enable).await;
        sysfs::write(&format!("{base}/disable"), disable).await;
    }
}

async fn hotplug(mode: Mode) {
    let i = mode.intensity();
    let v = if i > 0.5 { "0" } else { "1" };

    for path in paths::HOTPLUG_PATHS {
        sysfs::write(path, v).await;
    }
    sysfs::write(&format!("{}/cpuhotplug/enabled", paths::CPU), v).await;
}

async fn pewq(mode: Mode) {
    let i = mode.intensity();
    let v = if i > 0.5 { "N" } else { "Y" };
    sysfs::write(paths::PEWQ, v).await;
}

async fn lpm(mode: Mode) {
    if !sysfs::exists(paths::LPM).await {
        return;
    }

    let i = mode.intensity();
    let sleep_disabled = if i > 0.5 { "Y" } else { "N" };

    // Minimal LPM configuration - only safe parameters
    sysfs::write(&format!("{}/parameters/lpm_prediction", paths::LPM), "0").await;
    sysfs::write(&format!("{}/parameters/lpm_ipi_prediction", paths::LPM), "0").await;
    sysfs::write(&format!("{}/parameters/bias_hyst", paths::LPM), "2").await;
    sysfs::write(&format!("{}/parameters/sleep_disabled", paths::LPM), sleep_disabled).await;

    // Busy hysteresis for C-state entry timing
    let hyst_ns = if i > 0.5 { "1000000" } else { "2000000" };
    sysfs::write("/proc/sys/kernel/sched_busy_hyst_ns", hyst_ns).await;
    sysfs::write("/proc/sys/walt/sched_busy_hyst_ns", hyst_ns).await;
}

async fn devfreq_boost(mode: Mode) {
    let i = mode.intensity();

    // Only touch memory bus bandwidth devices - not audio/sensor/camera devfreq
    for pattern in paths::DEVFREQ_WHITELIST {
        for path in sysfs::glob(pattern).await {
            let base = path.display().to_string();
            if let Some(freqs) = sysfs::read(&format!("{base}/available_frequencies")).await {
                let freq_list: Vec<i64> = freqs
                    .split_whitespace()
                    .filter_map(|s| s.parse().ok())
                    .collect();

                if freq_list.is_empty() {
                    continue;
                }

                let max = freq_list.iter().max().copied().unwrap_or(0);
                let min = freq_list.iter().min().copied().unwrap_or(0);

                if max <= 0 || min <= 0 {
                    continue;
                }

                let floor_ratio = i * 0.5;
                let target = min + ((max - min) as f64 * floor_ratio).round() as i64;
                sysfs::write(&format!("{base}/min_freq"), &target.to_string()).await;
            }
        }
    }

    // Bus DCVS paths - reset to allow dynamic scaling
    for pattern in paths::BUS_DCVS_PATTERNS {
        sysfs::write_many(pattern, "0").await;
    }
}

async fn battery_saver(mode: Mode) {
    let i = mode.intensity();
    let v = if i < 0.1 { "Y" } else { "N" };
    sysfs::write(paths::BATTERY_SAVER, v).await;
}
