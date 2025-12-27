//! Network tuning - TCP/IP stack optimization.

use super::paths;
use crate::core::mode::Mode;
use crate::sense::profile;
use crate::util::sysfs;

pub async fn apply(_mode: Mode) {
    let cc = match sysfs::read(&format!("{}/tcp_available_congestion_control", paths::NET_IPV4)).await {
        Some(avail) => pick_congestion(&avail),
        None => "cubic",
    };

    let rmem = rmem_max().to_string();
    let wmem = wmem_max().to_string();
    let backlog = netdev_max_backlog().to_string();

    sysfs::write(&format!("{}/tcp_congestion_control", paths::NET_IPV4), cc).await;
    sysfs::write(&format!("{}/ip_no_pmtu_disc", paths::NET_IPV4), "0").await;
    sysfs::write(&format!("{}/tcp_ecn", paths::NET_IPV4), "1").await;
    sysfs::write(&format!("{}/tcp_timestamps", paths::NET_IPV4), "0").await;
    sysfs::write(&format!("{}/route/flush", paths::NET_IPV4), "1").await;
    sysfs::write(&format!("{}/tcp_rfc1337", paths::NET_IPV4), "1").await;
    sysfs::write(&format!("{}/tcp_tw_reuse", paths::NET_IPV4), "1").await;
    sysfs::write(&format!("{}/tcp_sack", paths::NET_IPV4), "1").await;
    sysfs::write(&format!("{}/tcp_fack", paths::NET_IPV4), "1").await;
    sysfs::write(&format!("{}/tcp_fastopen", paths::NET_IPV4), "3").await;
    sysfs::write(&format!("{}/tcp_no_metrics_save", paths::NET_IPV4), "1").await;
    sysfs::write(&format!("{}/tcp_syncookies", paths::NET_IPV4), "0").await;
    sysfs::write(&format!("{}/tcp_window_scaling", paths::NET_IPV4), "1").await;
    sysfs::write(&format!("{}/tcp_keepalive_probes", paths::NET_IPV4), "10").await;
    sysfs::write(&format!("{}/tcp_keepalive_intvl", paths::NET_IPV4), "30").await;
    sysfs::write(&format!("{}/tcp_fin_timeout", paths::NET_IPV4), "30").await;
    sysfs::write(&format!("{}/tcp_mtu_probing", paths::NET_IPV4), "1").await;
    sysfs::write(&format!("{}/tcp_slow_start_after_idle", paths::NET_IPV4), "0").await;
    sysfs::write(&format!("{}/rmem_default", paths::NET_CORE), "327680").await;
    sysfs::write(&format!("{}/rmem_max", paths::NET_CORE), &rmem).await;
    sysfs::write(&format!("{}/wmem_default", paths::NET_CORE), "327680").await;
    sysfs::write(&format!("{}/wmem_max", paths::NET_CORE), &wmem).await;
    sysfs::write(&format!("{}/optmem_max", paths::NET_CORE), "20480").await;
    sysfs::write(&format!("{}/netdev_max_backlog", paths::NET_CORE), &backlog).await;
    sysfs::write(&format!("{}/tcp_rmem", paths::NET_IPV4), "2097152 4194304 8388608").await;
    sysfs::write(&format!("{}/tcp_wmem", paths::NET_IPV4), "262144 524288 8388608").await;
}

fn pick_congestion(available: &str) -> &'static str {
    ["bbr2", "bbr", "westwood", "cubic"]
        .iter()
        .find(|c| available.contains(*c))
        .copied()
        .unwrap_or("cubic")
}

fn rmem_max() -> i64 {
    ((profile().total_ram_kb / 500) as i64).clamp(262144, 16777216)
}

fn wmem_max() -> i64 {
    rmem_max()
}

fn netdev_max_backlog() -> i64 {
    (profile().cores * 2500) as i64
}
