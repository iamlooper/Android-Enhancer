//! Memory/VM tuning - swappiness, dirty ratios, LMK, ZRAM.

use super::paths;
use crate::core::mode::Mode;
use crate::sense::profile;
use crate::util::sysfs;

pub async fn apply(mode: Mode, is_first: bool) {
    tokio::join!(
        tune_vm(mode, is_first),
        tune_lmk(),
        tune_zram(),
        tune_misc(),
    );
}

fn ram_factor() -> f64 {
    let gb = profile().ram_gb();
    match gb {
        0..=3 => 0.5,
        4..=5 => 0.75,
        6..=7 => 1.0,
        8..=11 => 1.25,
        _ => 1.5,
    }
}

// --- Formulas ---

fn swappiness(mode: Mode) -> i64 {
    let i = mode.intensity();
    let ram = ram_factor();
    let base = 100.0 / ram;
    let factor = 0.50 - i * 0.25;
    (base * factor).round().clamp(5.0, 200.0) as i64
}

fn dirty_background_ratio(mode: Mode) -> i64 {
    let i = mode.intensity();
    let ram = ram_factor();
    let base = 10.0 + ram * 5.0;
    let adjust = 5.0 * (1.0 - 2.0 * i);
    (base + adjust).round().clamp(3.0, 30.0) as i64
}

fn dirty_ratio(mode: Mode) -> i64 {
    let bg = dirty_background_ratio(mode);
    (bg * 3).clamp(10, 50)
}

fn vfs_cache_pressure(mode: Mode) -> i64 {
    let i = mode.intensity();
    let ram = ram_factor();
    let base = 100.0 / ram;
    let factor = 1.0 + i * 0.5;
    (base * factor).round().clamp(50.0, 200.0) as i64
}

fn min_free_kbytes() -> i64 {
    (profile().total_ram_kb as i64 / 128).clamp(8192, 262144)
}

fn watermark_scale_factor() -> i64 {
    let ram_mb = profile().total_ram_kb / 1024;
    ((360 * 1024) / ram_mb as i64).clamp(10, 500)
}

fn dirty_expire_centisecs(mode: Mode) -> i64 {
    let i = mode.intensity();
    (1000.0 + i * 5000.0).round() as i64
}

fn zram_wb_start_mins() -> &'static str {
    match profile().ram_gb() {
        0..=4 => "180",
        5..=6 => "240",
        7..=8 => "360",
        _ => "480",
    }
}

// --- Tuning Functions ---

async fn tune_vm(mode: Mode, is_first: bool) {
    // Only drop caches on first apply - doing it on every switch causes stalls
    if is_first {
        sysfs::write(&format!("{}/drop_caches", paths::VM), "3").await;
    }

    sysfs::write(&format!("{}/dirty_background_ratio", paths::VM), &dirty_background_ratio(mode).to_string()).await;
    sysfs::write(&format!("{}/dirty_ratio", paths::VM), &dirty_ratio(mode).to_string()).await;
    sysfs::write(&format!("{}/vfs_cache_pressure", paths::VM), &vfs_cache_pressure(mode).to_string()).await;
    sysfs::write(&format!("{}/swappiness", paths::VM), &swappiness(mode).to_string()).await;
    sysfs::write(&format!("{}/dirty_expire_centisecs", paths::VM), &dirty_expire_centisecs(mode).to_string()).await;
    sysfs::write(&format!("{}/dirty_writeback_centisecs", paths::VM), "3000").await;
    sysfs::write(&format!("{}/page-cluster", paths::VM), "0").await;
    sysfs::write(&format!("{}/stat_interval", paths::VM), "10").await;
    sysfs::write(&format!("{}/overcommit_memory", paths::VM), "1").await;
    sysfs::write(&format!("{}/overcommit_ratio", paths::VM), "100").await;
    sysfs::write(&format!("{}/laptop_mode", paths::VM), "0").await;
    sysfs::write(&format!("{}/min_free_kbytes", paths::VM), &min_free_kbytes().to_string()).await;
    sysfs::write(&format!("{}/oom_dump_tasks", paths::VM), "0").await;
    sysfs::write(&format!("{}/watermark_scale_factor", paths::VM), &watermark_scale_factor().to_string()).await;
    sysfs::write(paths::PROCESS_RECLAIM, "0").await;
}

async fn tune_lmk() {
    sysfs::write(&format!("{}/oom_reaper", paths::LMK), "1").await;
    sysfs::write(&format!("{}/lmk_fast_run", paths::LMK), "0").await;
    sysfs::write(&format!("{}/enable_adaptive_lmk", paths::LMK), "0").await;
}

async fn tune_zram() {
    let mins = zram_wb_start_mins();
    sysfs::write(&format!("{}/wb_start_mins", paths::ZRAM), mins).await;
}

async fn tune_misc() {
    sysfs::write(paths::LRU_GEN_TTL, "1000").await;
}
