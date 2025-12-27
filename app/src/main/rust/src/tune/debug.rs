//! Debug logging tuning.

use super::paths;
use crate::util::sysfs;

pub async fn apply() {
    tokio::join!(
        disable_debug_params(),
        disable_tracing(),
    );
}

async fn disable_debug_params() {
    for (path, value) in paths::DEBUG_PARAMS {
        sysfs::write(path, value).await;
    }

    for p in sysfs::glob("/sys/fs/ext4/*/mballoc_debug").await {
        sysfs::write(&p.to_string_lossy(), "0").await;
    }
    for p in sysfs::glob("/sys/fs/f2fs/*/inject_rate").await {
        sysfs::write(&p.to_string_lossy(), "0").await;
    }
}

async fn disable_tracing() {
    for path in paths::TRACING_PATHS {
        sysfs::write(path, "0").await;
    }
}
