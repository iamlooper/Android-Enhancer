//! Kernel scheduler tuning - CFS, EAS, HMP, schedtune, uclamp.

use super::paths;
use crate::core::mode::Mode;
use crate::sense::profile;
use crate::util::sysfs;

pub async fn apply(mode: Mode, is_first: bool) {
    tokio::join!(
        tune_cfs(mode, is_first),
        tune_sched_groups(mode),
        tune_uclamp(mode),
        tune_hmp(mode),
        tune_sched_features(is_first),
    );
}


fn device_factor() -> f64 {
    let core_factor = (profile().cores as f64 / 4.0).clamp(0.5, 2.0);
    let hetero_factor = if profile().freq_ratio > 2.0 { 1.2 } else { 1.0 };
    core_factor * hetero_factor
}

// --- Formulas ---

fn sched_latency_ns(mode: Mode) -> i64 {
    let i = mode.intensity();
    let base_ns = 6_000_000.0 * device_factor();
    let latency = base_ns * (1.0 - i * 0.67);
    latency.round() as i64
}

fn sched_min_granularity_ns(mode: Mode) -> i64 {
    let latency = sched_latency_ns(mode);
    let divisor = (profile().cores as i64 * 2).max(2);
    (latency / divisor).max(100_000)
}

fn sched_wakeup_granularity_ns(mode: Mode) -> i64 {
    let i = mode.intensity();
    let latency = sched_latency_ns(mode);
    let ratio = 0.75 - (i * 0.5);
    (latency as f64 * ratio).round() as i64
}

fn sched_migration_cost_ns(mode: Mode) -> i64 {
    let i = mode.intensity();
    let base = if profile().freq_ratio > 2.0 { 500_000 } else { 250_000 };
    let factor = 5.0 - (i * 4.5);
    (base as f64 * factor).round() as i64
}

fn sched_nr_migrate(mode: Mode) -> i64 {
    let i = mode.intensity();
    let base = profile().cores as i64 * 4;
    let factor = 2.0 - (i * 1.5);
    (base as f64 * factor).round().max(4.0) as i64
}

fn perf_cpu_time_max_percent(mode: Mode) -> i64 {
    let i = mode.intensity();
    (2.0 + i * 23.0).round() as i64
}

fn ta_schedtune_boost(mode: Mode) -> i64 {
    let i = mode.intensity();
    (i * 50.0).round() as i64
}

fn hmp_up_threshold(mode: Mode) -> i64 {
    let i = mode.intensity();
    let base = if profile().big_cores <= 2 { 700 } else { 600 };
    (base as f64 + 200.0 * (1.0 - 2.0 * i)).round() as i64
}

fn hmp_down_threshold(mode: Mode) -> i64 {
    let up = hmp_up_threshold(mode);
    let i = mode.intensity();
    let ratio = 0.5 - (i * 0.2);
    (up as f64 * ratio).round().max(100.0) as i64
}

// --- Tuning Functions ---

async fn tune_cfs(mode: Mode, is_first: bool) {
    let i = mode.intensity();
    let eas = "1";
    let boost_top_app = if i < 0.1 { "0" } else { "1" };
    let conservative = if i < 0.2 { "1" } else { "0" };

    sysfs::write(&format!("{}/sched_latency_ns", paths::KERN), &sched_latency_ns(mode).to_string()).await;
    sysfs::write(&format!("{}/sched_min_granularity_ns", paths::KERN), &sched_min_granularity_ns(mode).to_string()).await;
    sysfs::write(&format!("{}/sched_wakeup_granularity_ns", paths::KERN), &sched_wakeup_granularity_ns(mode).to_string()).await;
    sysfs::write(&format!("{}/sched_migration_cost_ns", paths::KERN), &sched_migration_cost_ns(mode).to_string()).await;
    sysfs::write(&format!("{}/sched_nr_migrate", paths::KERN), &sched_nr_migrate(mode).to_string()).await;
    sysfs::write(&format!("{}/perf_cpu_time_max_percent", paths::KERN), &perf_cpu_time_max_percent(mode).to_string()).await;

    sysfs::write(&format!("{}/sched_energy_aware", paths::KERN), eas).await;
    sysfs::write(&format!("{}/eas/enable", paths::CPU), eas).await;
    sysfs::write(&format!("{}/sched_boost_top_app", paths::KERN), boost_top_app).await;
    sysfs::write(&format!("{}/sched_conservative_pl", paths::KERN), conservative).await;

    sysfs::write(&format!("{}/sched_child_runs_first", paths::KERN), "0").await;
    sysfs::write(&format!("{}/sched_autogroup_enabled", paths::KERN), "0").await;
    sysfs::write(&format!("{}/sched_tunable_scaling", paths::KERN), "0").await;
    sysfs::write(&format!("{}/sched_schedstats", paths::KERN), "0").await;
    sysfs::write(&format!("{}/sched_cstate_aware", paths::KERN), "1").await;
    sysfs::write(&format!("{}/timer_migration", paths::KERN), "0").await;
    sysfs::write(&format!("{}/sched_boost", paths::KERN), "0").await;
    sysfs::write(&format!("{}/sched_walt_rotate_big_tasks", paths::KERN), "1").await;
    sysfs::write(&format!("{}/sched_prefer_sync_wakee_to_waker", paths::KERN), "1").await;
    sysfs::write(&format!("{}/printk_devkmsg", paths::KERN), "off").await;

    // RCU configuration - only on first apply to avoid callback stalls
    if is_first {
        sysfs::write(paths::RCU_EXPEDITED, "0").await;
        sysfs::write(paths::RCU_NORMAL, "1").await;
        sysfs::write(paths::LDISC_AUTOLOAD, "0").await;
    }
}

async fn tune_sched_groups(mode: Mode) {
    if !sysfs::exists(paths::STUNE).await {
        return;
    }

    let i = mode.intensity();
    let ta_boost = ta_schedtune_boost(mode).to_string();
    let perf_v = if i > 0.5 { "1" } else { "0" };
    let ta_sched_boost = if i > 0.9 { "15" } else { "0" };

    sysfs::write(&format!("{}/background/schedtune.boost", paths::STUNE), "0").await;
    sysfs::write(&format!("{}/background/schedtune.colocate", paths::STUNE), "0").await;
    sysfs::write(&format!("{}/background/schedtune.prefer_idle", paths::STUNE), "0").await;
    sysfs::write(&format!("{}/background/schedtune.prefer_perf", paths::STUNE), "0").await;

    sysfs::write(&format!("{}/foreground/schedtune.boost", paths::STUNE), "0").await;
    sysfs::write(&format!("{}/foreground/schedtune.colocate", paths::STUNE), perf_v).await;
    sysfs::write(&format!("{}/foreground/schedtune.prefer_idle", paths::STUNE), perf_v).await;

    sysfs::write(&format!("{}/top-app/schedtune.boost", paths::STUNE), &ta_boost).await;
    sysfs::write(&format!("{}/top-app/schedtune.colocate", paths::STUNE), "1").await;
    sysfs::write(&format!("{}/top-app/schedtune.prefer_idle", paths::STUNE), "1").await;
    sysfs::write(&format!("{}/top-app/schedtune.prefer_perf", paths::STUNE), "1").await;
    sysfs::write(&format!("{}/top-app/schedtune.sched_boost", paths::STUNE), ta_sched_boost).await;
}

async fn tune_uclamp(mode: Mode) {
    if !sysfs::exists(&format!("{}/top-app/cpu.uclamp.max", paths::CPUCTL)).await {
        return;
    }

    let i = mode.intensity();
    let ta_min = (i * 1024.0).round() as i64;
    let ta_min_str = if ta_min >= 1024 { "max".to_string() } else { ta_min.to_string() };
    let boosted = if i > 0.5 { "1" } else { "0" };
    let bg_max = (1024.0 - i * 512.0).round() as i64;

    sysfs::write(&format!("{}/sched_util_clamp_min", paths::KERN), "1024").await;
    sysfs::write(&format!("{}/sched_util_clamp_max", paths::KERN), "1024").await;
    sysfs::write(&format!("{}/top-app/cpu.uclamp.max", paths::CPUCTL), "max").await;
    sysfs::write(&format!("{}/top-app/cpu.uclamp.min", paths::CPUCTL), &ta_min_str).await;
    sysfs::write(&format!("{}/top-app/cpu.uclamp.boosted", paths::CPUCTL), boosted).await;
    sysfs::write(&format!("{}/top-app/cpu.uclamp.latency_sensitive", paths::CPUCTL), "1").await;
    sysfs::write(&format!("{}/foreground/cpu.uclamp.max", paths::CPUCTL), "max").await;
    sysfs::write(&format!("{}/foreground/cpu.uclamp.min", paths::CPUCTL), "0").await;
    sysfs::write(&format!("{}/background/cpu.uclamp.max", paths::CPUCTL), &bg_max.to_string()).await;
    sysfs::write(&format!("{}/background/cpu.uclamp.min", paths::CPUCTL), "0").await;
}

async fn tune_hmp(mode: Mode) {
    if !sysfs::exists(paths::HMP).await {
        return;
    }

    let i = mode.intensity();
    let boost = if i > 0.5 { "1" } else { "0" };

    sysfs::write(&format!("{}/boost", paths::HMP), boost).await;
    sysfs::write(&format!("{}/down_compensation_enabled", paths::HMP), "1").await;
    sysfs::write(&format!("{}/family_boost", paths::HMP), boost).await;
    sysfs::write(&format!("{}/semiboost", paths::HMP), boost).await;
    sysfs::write(&format!("{}/up_threshold", paths::HMP), &hmp_up_threshold(mode).to_string()).await;
    sysfs::write(&format!("{}/down_threshold", paths::HMP), &hmp_down_threshold(mode).to_string()).await;
}

async fn tune_sched_features(is_first: bool) {
    // Only configure sched_features on first apply - runtime changes can stall scheduler
    if !is_first {
        return;
    }

    let path = format!("{}/sched_features", paths::DEBUG_FS);
    if !sysfs::exists(&path).await {
        return;
    }

    sysfs::write(&path, "NEXT_BUDDY").await;
    sysfs::write(&path, "NO_TTWU_QUEUE").await;
    sysfs::write(&path, "ENERGY_AWARE").await;
}
