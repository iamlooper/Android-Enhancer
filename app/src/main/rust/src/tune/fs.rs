//! Filesystem tuning.

use super::paths;
use crate::core::mode::Mode;
use crate::util::sysfs;

pub async fn apply(_mode: Mode) {
    sysfs::write(&format!("{}/dir-notify-enable", paths::FS), "0").await;
    sysfs::write(&format!("{}/lease-break-time", paths::FS), "15").await;
    sysfs::write(&format!("{}/leases-enable", paths::FS), "1").await;
    sysfs::write(paths::DYN_FSYNC, "1").await;
    sysfs::write(paths::MMC_CRC, "N").await;
}
