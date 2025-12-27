//! CPU tuning - governor, frequency, boost.

use super::paths;
use crate::core::mode::Mode;
use crate::sense::profile;
use crate::util::sysfs;

pub async fn apply(mode: Mode) {
    tokio::join!(
        tune_governors(mode),
        tune_boost_params(mode),
    );
}

// --- Formulas ---

/// hispeed_load: Load threshold to jump to hispeed_freq.
fn hispeed_load(mode: Mode) -> i64 {
    let i = mode.intensity();
    let margin = 0.1 + i * 0.3;
    ((1.0 - margin) * 100.0).round() as i64
}

/// up_rate_limit_us: Minimum time before scaling up
fn up_rate_limit_us(mode: Mode) -> i64 {
    let i = mode.intensity();
    let base = (80_000.0 / profile().cores.max(1) as f64).round();
    let factor = 4.0 * (1.0 - i);
    (base * factor).round() as i64
}

/// down_rate_limit_us: Minimum time before scaling down
fn down_rate_limit_us(mode: Mode) -> i64 {
    let base = (80_000.0 / profile().cores.max(1) as f64).round();
    let factor = match mode {
        Mode::Powersaver => 8.0,
        Mode::Balanced | Mode::Auto => 4.0,
        Mode::Performance => 2.0,
        Mode::Gaming => 6.0,
    };
    (base * factor).round() as i64
}

/// Frequency cap as ratio of max freq.
fn freq_cap_ratio(mode: Mode) -> f64 {
    let i = mode.intensity();
    0.70 + i * 0.30
}

/// Boost duration in milliseconds
fn boost_ms(mode: Mode) -> i64 {
    let i = mode.intensity();
    (60.0 + i * 140.0).round() as i64
}

/// Stune boost value (0-100)
fn stune_boost(mode: Mode) -> i64 {
    let i = mode.intensity();
    (i * 50.0).round() as i64
}

// --- Governor Tuning ---

async fn tune_governors(mode: Mode) {
    let i = mode.intensity();
    let hispeed = hispeed_load(mode).to_string();
    let up_rate = up_rate_limit_us(mode).to_string();
    let dn_rate = down_rate_limit_us(mode).to_string();
    let iowait = if i < 0.1 { "0" } else { "1" };
    let freq_ratio = freq_cap_ratio(mode);

    for path in sysfs::glob(&format!("{}/cpu*/cpufreq", paths::CPU)).await {
        let base = path.display().to_string();
        let governors = sysfs::read(&format!("{base}/scaling_available_governors")).await.unwrap_or_default();

        // Governor selection
        let gov = if i > 0.9 && governors.contains("performance") {
            "performance"
        } else if governors.contains("schedutil") {
            "schedutil"
        } else if governors.contains("interactive") {
            "interactive"
        } else if governors.contains("ondemand") {
            "ondemand"
        } else {
            continue;
        };
        sysfs::write(&format!("{base}/scaling_governor"), gov).await;

        if gov == "schedutil" {
            sysfs::write(&format!("{base}/schedutil/up_rate_limit_us"), &up_rate).await;
            sysfs::write(&format!("{base}/schedutil/down_rate_limit_us"), &dn_rate).await;
            sysfs::write(&format!("{base}/schedutil/pl"), "1").await;
            sysfs::write(&format!("{base}/schedutil/hispeed_load"), &hispeed).await;
            sysfs::write(&format!("{base}/schedutil/iowait_boost_enable"), iowait).await;
            if let Some(max) = sysfs::read_i64(&format!("{base}/cpuinfo_max_freq")).await {
                sysfs::write(&format!("{base}/schedutil/hispeed_freq"), &max.to_string()).await;
            }
        }

        if gov == "interactive" {
            let io_busy = if i > 0.5 { "1" } else { "0" };
            let fast_ramp = if i < 0.2 { "1" } else { "0" };
            sysfs::write(&format!("{base}/interactive/timer_rate"), &dn_rate).await;
            sysfs::write(&format!("{base}/interactive/boost"), "0").await;
            sysfs::write(&format!("{base}/interactive/timer_slack"), &up_rate).await;
            sysfs::write(&format!("{base}/interactive/use_migration_notif"), "1").await;
            sysfs::write(&format!("{base}/interactive/use_sched_load"), "1").await;
            sysfs::write(&format!("{base}/interactive/go_hispeed_load"), &hispeed).await;
            sysfs::write(&format!("{base}/interactive/io_is_busy"), io_busy).await;
            sysfs::write(&format!("{base}/interactive/fast_ramp_down"), fast_ramp).await;
            if let Some(max) = sysfs::read_i64(&format!("{base}/cpuinfo_max_freq")).await {
                sysfs::write(&format!("{base}/interactive/hispeed_freq"), &max.to_string()).await;
            }
        }

        // Frequency cap based on mode
        if let Some(max) = sysfs::read_i64(&format!("{base}/cpuinfo_max_freq")).await {
            let cap = (max as f64 * freq_ratio) as i64;
            let floor = profile().min_freq_khz + (profile().max_freq_khz - profile().min_freq_khz) / 3;
            sysfs::write(&format!("{base}/scaling_max_freq"), &cap.max(floor).to_string()).await;
        }
    }

    // Global boost setting
    let boost = if i < 0.1 { "0" } else { "1" };
    sysfs::write(paths::CPUFREQ_BOOST, boost).await;
    sysfs::write_many(&format!("{}/cpu*/cpufreq/boost", paths::CPU), boost).await;
}

async fn tune_boost_params(mode: Mode) {
    let boost_dur = boost_ms(mode).to_string();
    let stune = stune_boost(mode).to_string();

    if sysfs::exists(paths::CPU_BOOST).await {
        sysfs::write(&format!("{}/input_boost_ms", paths::CPU_BOOST), &boost_dur).await;
        sysfs::write(&format!("{}/input_boost_enabled", paths::CPU_BOOST), "1").await;
        sysfs::write(&format!("{}/sched_boost_on_input", paths::CPU_BOOST), "0").await;
        sysfs::write(&format!("{}/powerkey_input_boost_ms", paths::CPU_BOOST), "500").await;
        sysfs::write(&format!("{}/dynamic_stune_boost", paths::CPU_BOOST), &stune).await;
        sysfs::write(&format!("{}/dynamic_stune_boost_ms", paths::CPU_BOOST), "700").await;
    }

    if sysfs::exists(paths::CPU_INPUT_BOOST).await {
        sysfs::write(&format!("{}/input_boost_duration", paths::CPU_INPUT_BOOST), &boost_dur).await;
    }
}

pub async fn boost(strength: f64) {
    let range = (profile().max_freq_khz - profile().min_freq_khz).max(1) as f64;
    let little = (profile().min_freq_khz as f64 + range * (0.3 + strength * 0.3)) as i64;
    let big = (profile().min_freq_khz as f64 + range * (0.55 + strength * 0.35)) as i64;
    let dur = (40.0 + strength * 160.0).round() as i32;
    let wake = (60.0 + strength * 190.0).round() as i32;
    let stune = (strength * 50.0).round() as i32;

    sysfs::write(&format!("{}/input_boost_duration", paths::CPU_INPUT_BOOST), &dur.to_string()).await;
    sysfs::write(&format!("{}/wake_boost_duration", paths::CPU_INPUT_BOOST), &wake.to_string()).await;
    sysfs::write(&format!("{}/input_boost_freq_lp", paths::CPU_INPUT_BOOST), &little.to_string()).await;
    sysfs::write(&format!("{}/input_boost_freq_hp", paths::CPU_INPUT_BOOST), &big.to_string()).await;
    sysfs::write(&format!("{}/dynamic_stune_boost", paths::CPU_INPUT_BOOST), &stune.to_string()).await;
    sysfs::write(&format!("{}/input_boost_ms", paths::CPU_BOOST), &dur.to_string()).await;

    let sched = if strength < 0.3 { 0 } else if strength < 0.7 { 1 } else { 2 };
    sysfs::write(&format!("{}/sched_boost", paths::KERN), &sched.to_string()).await;
}
