//! Consolidated sysfs path constants.
//! 
//! ALL kernel paths used across the codebase are defined here.

// --- Kernel Parameters ---
pub const KERN: &str = "/proc/sys/kernel";
pub const RCU_EXPEDITED: &str = "/sys/kernel/rcu_expedited";
pub const RCU_NORMAL: &str = "/sys/kernel/rcu_normal";
pub const LDISC_AUTOLOAD: &str = "/proc/sys/dev/tty/ldisc_autoload";

// --- Virtual Memory ---
pub const VM: &str = "/proc/sys/vm";
pub const PROCESS_RECLAIM: &str = "/sys/module/process_reclaim/parameters/enable_process_reclaim";
pub const LRU_GEN_TTL: &str = "/sys/kernel/mm/lru_gen/min_ttl_ms";

// --- Filesystem ---
pub const FS: &str = "/proc/sys/fs";
pub const DYN_FSYNC: &str = "/sys/kernel/dyn_fsync/Dyn_fsync_active";
pub const MMC_CRC: &str = "/sys/module/mmc_core/parameters/use_spi_crc";

// --- Network ---
pub const NET_IPV4: &str = "/proc/sys/net/ipv4";
pub const NET_CORE: &str = "/proc/sys/net/core";

// --- Memory Management ---
pub const LMK: &str = "/sys/module/lowmemorykiller/parameters";
pub const ZRAM: &str = "/sys/module/zram/parameters";

// --- Thermal ---
pub const THERMAL: &str = "/sys/class/thermal";

// --- Scheduler ---
pub const STUNE: &str = "/dev/stune";
pub const CPUCTL: &str = "/dev/cpuctl";
pub const HMP: &str = "/sys/kernel/hmp";

// --- CPU ---
pub const CPU: &str = "/sys/devices/system/cpu";
pub const CPU_BOOST: &str = "/sys/module/cpu_boost/parameters";
pub const CPU_INPUT_BOOST: &str = "/sys/module/cpu_input_boost/parameters";
pub const CPUFREQ_BOOST: &str = "/sys/devices/system/cpu/cpufreq/boost";

// --- GPU ---
pub const GPU_KGSL: &str = "/sys/class/kgsl/kgsl-3d0";
pub const GPU_KERNEL: &str = "/sys/kernel/gpu";

/// GPU frequency pairs (max_path, min_path)
pub const GPU_FREQ_PAIRS: &[(&str, &str)] = &[
    ("/sys/class/kgsl/kgsl-3d0/devfreq/max_freq", "/sys/class/kgsl/kgsl-3d0/devfreq/min_freq"),
    ("/sys/class/devfreq/mali0/max_freq", "/sys/class/devfreq/mali0/min_freq"),
    ("/sys/kernel/gpu/gpu_max_clock", "/sys/kernel/gpu/gpu_min_clock"),
    ("/sys/kernel/gpu/max_freq", "/sys/kernel/gpu/min_freq"),
    ("/sys/class/devfreq/pvr/max_freq", "/sys/class/devfreq/pvr/min_freq"),
    ("/sys/class/devfreq/gpufreq/max_freq", "/sys/class/devfreq/gpufreq/min_freq"),
];

/// GPU load paths (for sensing)
pub const GPU_LOAD_PATHS: &[&str] = &[
    "/sys/class/kgsl/kgsl-3d0/gpubusy",
    "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage",
    "/sys/kernel/gpu/gpu_busy",
    "/sys/class/misc/mali0/device/utilisation",
    "/sys/kernel/debug/mali0/utilization",
    "/sys/class/devfreq/gpufreq/load",
    "/sys/class/drm/card0/device/gpu_busy_percentage",
    "/sys/class/drm/card1/device/gpu_busy_percentage",
    "/sys/devices/gpu.0/load",
    "/sys/devices/platform/gpu.0/load",
    "/sys/kernel/debug/pvr/gpu_utilisation",
    "/sys/class/devfreq/mali/load",
    "/sys/class/devfreq/gpu/load",
];

/// GPU max freq paths (for topology detection)
#[allow(dead_code)]
pub const GPU_MAX_FREQ_PATHS: &[&str] = &[
    "/sys/class/kgsl/kgsl-3d0/devfreq/max_freq",
    "/sys/kernel/gpu/gpu_max_clock",
    "/sys/class/devfreq/mali0/max_freq",
    "/sys/class/devfreq/gpufreq/max_freq",
];

// --- Power Management ---
pub const LPM: &str = "/sys/module/lpm_levels";
pub const PEWQ: &str = "/sys/module/workqueue/parameters/power_efficient";
pub const BATTERY_SAVER: &str = "/sys/module/battery_saver/parameters/enabled";

/// Hotplug paths
pub const HOTPLUG_PATHS: &[&str] = &[
    "/sys/power/cpuhotplug/enable",
    "/sys/power/cpuhotplug/enabled",
];

// --- Debug ---
pub const DEBUG_FS: &str = "/sys/kernel/debug";
pub const TRACING_PATHS: &[&str] = &[
    "/sys/kernel/debug/tracing/tracing_on",
    "/sys/kernel/tracing/tracing_on",
];

/// Debug parameters to disable
pub const DEBUG_PARAMS: &[(&str, &str)] = &[
    ("/sys/module/lowmemorykiller/parameters/debug_level", "0"),
    ("/sys/module/alarm_dev/parameters/debug_mask", "0"),
    ("/sys/module/binder/parameters/debug_mask", "0"),
    ("/sys/module/kernel/parameters/debug", "0"),
    ("/sys/module/printk/parameters/console_suspend", "0"),
    ("/sys/module/msm_poweroff/parameters/download_mode", "0"),
    ("/sys/module/subsystem_restart/parameters/enable_ramdumps", "0"),
    ("/sys/module/edac_core/parameters/edac_mc_log_ce", "0"),
    ("/sys/module/edac_core/parameters/edac_mc_log_ue", "0"),
    ("/sys/module/spurious/parameters/noirqdebug", "1"),
];

// --- Block I/O ---
pub const BLOCK_GLOB: &str = "/sys/block/*/queue/";
pub const BLOCK_ZRAM_GLOB: &str = "/sys/block/zram*/queue/";
pub const BLOCK_EMMC_GLOB: &str = "/sys/block/mmcblk*/queue/read_ahead_kb";
pub const BLOCK_SD_GLOB: &str = "/sys/block/sd*/queue/read_ahead_kb";

// --- Devfreq (safe memory bus devices only) ---
pub const DEVFREQ_WHITELIST: &[&str] = &[
    "/sys/class/devfreq/*cpubw",
    "/sys/class/devfreq/*llccbw",
    "/sys/class/devfreq/*cpu-cpu-llcc-bw",
    "/sys/class/devfreq/*cpu-llcc-ddr-bw",
];

pub const BUS_DCVS_PATTERNS: &[&str] = &[
    "/sys/devices/system/cpu/bus_dcvs/DDR/*/min_freq",
    "/sys/devices/system/cpu/bus_dcvs/LLCC/*/min_freq",
    "/sys/devices/system/cpu/bus_dcvs/L3/*/min_freq",
];
