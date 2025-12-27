//! Thermal policy tuning.

use super::paths;
use crate::core::mode::Mode;
use crate::util::sysfs;

pub async fn apply(_mode: Mode) {
    // Always use step_wise policy - never disable kernel thermal protection.
    let policy = "step_wise";
    for path in sysfs::glob(&format!("{}/*/policy", paths::THERMAL)).await {
        sysfs::write(&path.to_string_lossy(), policy).await;
    }
}
