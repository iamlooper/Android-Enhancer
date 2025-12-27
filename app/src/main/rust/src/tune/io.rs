//! I/O scheduler tuning.

use super::paths;
use crate::core::mode::Mode;
use crate::sense::profile;
use crate::util::sysfs;

pub async fn apply(mode: Mode) {
    let readahead = readahead_kb(mode).to_string();
    let nr_req = nr_requests(mode).to_string();
    let rq_aff = rq_affinity(mode).to_string();
    let nomerge = nomerges(mode).to_string();
    let i = mode.intensity();
    let lat = if i < 0.2 { "0" } else { "1" };

    for path in sysfs::glob(paths::BLOCK_GLOB).await {
        let base = path.display().to_string();

        // Select best scheduler
        if let Some(sched) = sysfs::read(&format!("{base}/scheduler")).await.and_then(|a| pick_scheduler(&a)) {
            sysfs::write(&format!("{base}/scheduler"), sched).await;
        }

        sysfs::write(&format!("{base}/add_random"), "0").await;
        sysfs::write(&format!("{base}/iostats"), "0").await;
        sysfs::write(&format!("{base}/rotational"), "0").await;
        sysfs::write(&format!("{base}/read_ahead_kb"), &readahead).await;
        sysfs::write(&format!("{base}/nomerges"), &nomerge).await;
        sysfs::write(&format!("{base}/rq_affinity"), &rq_aff).await;
        sysfs::write(&format!("{base}/nr_requests"), &nr_req).await;

        let iosched = format!("{base}/iosched/");
        if sysfs::exists(&iosched).await {
            sysfs::write(&format!("{iosched}slice_idle"), "0").await;
            sysfs::write(&format!("{iosched}group_idle"), "1").await;
            sysfs::write(&format!("{iosched}quantum"), "16").await;
            sysfs::write(&format!("{iosched}back_seek_penalty"), "1").await;
            sysfs::write(&format!("{iosched}low_latency"), lat).await;
        }
    }

    // ZRAM: no readahead needed
    for path in sysfs::glob(paths::BLOCK_ZRAM_GLOB).await {
        let base = path.display().to_string();
        sysfs::write(&format!("{base}/read_ahead_kb"), "0").await;
    }
}

// --- Formulas ---

fn readahead_kb(mode: Mode) -> i64 {
    let base = if profile().is_ufs { 64 } else { 128 };
    let factor = match mode {
        Mode::Powersaver => 1.0,
        Mode::Balanced | Mode::Auto => 1.5,
        Mode::Performance => 0.5,
        Mode::Gaming => 4.0,
    };
    (base as f64 * factor).round().max(4.0) as i64
}

fn nr_requests(mode: Mode) -> i64 {
    let i = mode.intensity();
    let base = if profile().is_ufs { 32 } else { 64 };
    let factor = 4.0 - i * 3.0;
    (base as f64 * factor).round().max(16.0) as i64
}

fn rq_affinity(mode: Mode) -> i64 {
    let i = mode.intensity();
    if i > 0.5 { 2 }
    else if i > 0.2 { 1 }
    else { 0 }
}

fn nomerges(mode: Mode) -> i64 {
    let i = mode.intensity();
    if i > 0.7 { 2 } else { 0 }
}

fn pick_scheduler(avail: &str) -> Option<&'static str> {
    ["mq-deadline", "deadline", "bfq", "cfq", "noop", "none"]
        .iter()
        .find(|s| avail.contains(*s))
        .copied()
}

pub async fn boost(strength: f64) {
    let kb = (128.0 + strength * 900.0).round() as i32;
    let kb_str = kb.to_string();
    sysfs::write_many(paths::BLOCK_EMMC_GLOB, &kb_str).await;
    sysfs::write_many(paths::BLOCK_SD_GLOB, &kb_str).await;
}
