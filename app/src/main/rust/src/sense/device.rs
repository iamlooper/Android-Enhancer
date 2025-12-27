//! Device hardware profile and runtime load sensing.
//!
//! - `DeviceProfile`: Hardware topology + runtime info (battery, screen)
//! - `LoadSensor`: CPU/GPU load polling with cancellation

use crate::tune::paths;
use crate::util::sysfs;
use once_cell::sync::OnceCell;
use parking_lot::RwLock;
use std::sync::Arc;
use std::time::Duration;
use sysinfo::System;
use tokio::fs;
use tokio::sync::mpsc;
use tokio::time::{interval, timeout};
use tokio_util::sync::CancellationToken;

/// Device profile - initialized on first access
static PROFILE: OnceCell<Arc<RwLock<DeviceProfile>>> = OnceCell::new();

/// Load polling interval
const POLL_INTERVAL: Duration = Duration::from_millis(50);

/// Timeout for individual file reads
const READ_TIMEOUT: Duration = Duration::from_millis(30);

// --- GPU Vendor ---

/// GPU vendor for safe, vendor-aware tuning
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum GpuVendor {
    Adreno,   // Qualcomm (kgsl paths)
    Mali,     // ARM (mali paths)
    PowerVR,  // Imagination (pvr paths)
    Unknown,  // Safe fallback - CPU-only gaming detection
}

// --- Device Profile ---

/// Complete device profile: hardware topology + runtime info
#[derive(Clone, Debug)]
pub struct DeviceProfile {
    // Hardware topology (static)
    pub cores: usize,
    pub big_cores: usize,
    pub max_freq_khz: i64,
    pub min_freq_khz: i64,
    pub freq_ratio: f64,
    pub total_ram_kb: u64,
    #[allow(dead_code)]
    pub gpu_available: bool,
    #[allow(dead_code)]
    pub gpu_vendor: GpuVendor,
    pub is_ufs: bool,

    // Runtime info (from JNI)
    pub battery_capacity_mah: i32,
    pub battery_level: i32,
    pub is_charging: bool,
    pub screen_dpi: i32,
}

impl DeviceProfile {
    /// Detect device profile
    async fn detect() -> Self {
        let mut sys = System::new();
        sys.refresh_memory();

        let cores = sys.cpus().len().max(1);
        let total_ram_kb = sys.total_memory() / 1024;

        let (max_freq_khz, min_freq_khz, big_cores) = detect_cpu_topology(cores).await;
        let freq_ratio = if min_freq_khz > 0 {
            max_freq_khz as f64 / min_freq_khz as f64
        } else {
            1.0
        };

        let gpu_vendor = detect_gpu_vendor().await;
        let gpu_available = gpu_vendor != GpuVendor::Unknown;
        let is_ufs = detect_ufs().await;

        Self {
            cores,
            big_cores,
            max_freq_khz,
            min_freq_khz,
            freq_ratio,
            total_ram_kb,
            gpu_available,
            gpu_vendor,
            is_ufs,
            // Runtime defaults
            battery_capacity_mah: 4000,
            battery_level: 100,
            is_charging: false,
            screen_dpi: 160,
        }
    }

    #[inline]
    pub fn ram_gb(&self) -> u64 {
        self.total_ram_kb / 1_000_000
    }

    /// Dynamic low battery threshold: capacity_mah / 200, clamped to 15-30%
    pub fn low_battery_threshold(&self) -> i32 {
        (self.battery_capacity_mah / 200).clamp(15, 30)
    }

    /// DPI scale factor relative to mdpi (160)
    pub fn dpi_scale(&self) -> f64 {
        (self.screen_dpi as f64 / 160.0).max(0.5)
    }
}

// --- Public API ---

/// Initialize device profile (call from async context after tokio is running)
pub async fn init_profile() {
    let profile = DeviceProfile::detect().await;
    let _ = PROFILE.set(Arc::new(RwLock::new(profile)));
}

/// Get current device profile snapshot
pub fn profile() -> DeviceProfile {
    PROFILE
        .get()
        .map(|p| p.read().clone())
        .unwrap_or_else(|| DeviceProfile {
            cores: 4,
            big_cores: 2,
            max_freq_khz: 1_500_000,
            min_freq_khz: 300_000,
            freq_ratio: 5.0,
            total_ram_kb: 4_000_000,
            gpu_available: false,
            gpu_vendor: GpuVendor::Unknown,
            is_ufs: false,
            battery_capacity_mah: 4000,
            battery_level: 100,
            is_charging: false,
            screen_dpi: 160,
        })
}

/// Update battery info from JNI
pub fn set_battery_info(level: i32, capacity_mah: i32, is_charging: bool) {
    if let Some(p) = PROFILE.get() {
        let mut guard = p.write();
        guard.battery_level = level;
        guard.battery_capacity_mah = capacity_mah.max(1000);
        guard.is_charging = is_charging;
    }
}

/// Update screen info from JNI
pub fn set_screen_info(dpi: i32, _width_px: i32, _height_px: i32) {
    if let Some(p) = PROFILE.get() {
        let mut guard = p.write();
        guard.screen_dpi = dpi.clamp(120, 640);
    }
}

async fn detect_cpu_topology(cores: usize) -> (i64, i64, usize) {
    let mut maxes = Vec::with_capacity(cores);
    let mut mins = Vec::with_capacity(cores);

    for i in 0..cores {
        let base = format!("{}/cpu{i}/cpufreq", paths::CPU);
        if let Some(f) = sysfs::read_i64(&format!("{base}/cpuinfo_max_freq")).await {
            maxes.push(f);
        }
        if let Some(f) = sysfs::read_i64(&format!("{base}/cpuinfo_min_freq")).await {
            mins.push(f);
        }
    }

    let max_freq = *maxes.iter().max().unwrap_or(&1_500_000);
    let min_freq = *mins.iter().min().unwrap_or(&300_000);

    // Big cores have frequency >= 2/3 of max
    let threshold = max_freq - (max_freq - min_freq) / 3;
    let big_cores = maxes.iter().filter(|&&f| f >= threshold).count().max(1);

    (max_freq, min_freq.max(100_000), big_cores)
}

async fn detect_gpu_vendor() -> GpuVendor {
    // Adreno (Qualcomm) - kgsl paths
    if sysfs::exists(paths::GPU_KGSL).await {
        return GpuVendor::Adreno;
    }
    // Mali (ARM) - mali devfreq paths
    if sysfs::exists("/sys/class/devfreq/mali0").await
        || sysfs::exists("/sys/kernel/gpu").await
    {
        return GpuVendor::Mali;
    }
    // PowerVR (Imagination) - pvr paths
    if sysfs::exists("/sys/class/devfreq/pvr").await {
        return GpuVendor::PowerVR;
    }
    // Unknown - safe fallback
    GpuVendor::Unknown
}

async fn detect_ufs() -> bool {
    // UFS uses SCSI host interface
    if sysfs::exists("/sys/class/scsi_host/host0").await {
        return true;
    }
    // Check for sd* block devices (SCSI = UFS on mobile)
    if let Ok(mut entries) = fs::read_dir("/sys/block").await {
        while let Ok(Some(entry)) = entries.next_entry().await {
            if entry.file_name().to_string_lossy().starts_with("sd") {
                return true;
            }
        }
    }
    false
}

// --- Load Sensing ---

#[derive(Clone, Debug, Default)]
pub struct Load {
    pub cpu: f64,
    pub gpu: f64,
    pub gpu_available: bool,
}

pub struct LoadSensor {
    cancel: CancellationToken,
    rx: Option<mpsc::Receiver<Load>>,
}

impl LoadSensor {
    pub async fn start() -> Self {
        let (tx, rx) = mpsc::channel(16);
        let cancel = CancellationToken::new();
        let cancel_clone = cancel.clone();

        tokio::spawn(async move {
            run_load_sensor(cancel_clone, tx).await;
        });

        Self { cancel, rx: Some(rx) }
    }

    /// Take ownership of the receiver (can only be called once)
    pub fn take_receiver(&mut self) -> mpsc::Receiver<Load> {
        self.rx.take().expect("LoadSensor::take_receiver called more than once")
    }

    /// Check if the receiver is still available.
    pub fn has_receiver(&self) -> bool {
        self.rx.is_some()
    }

    pub fn stop(self) {
        self.cancel.cancel();
    }
}

impl Drop for LoadSensor {
    fn drop(&mut self) {
        self.cancel.cancel();
    }
}

async fn run_load_sensor(cancel: CancellationToken, tx: mpsc::Sender<Load>) {
    let mut prev_cpu: Option<CpuSnap> = None;
    let gpu_path = find_gpu_path().await;
    let gpu_available = gpu_path.is_some();
    let mut ticker = interval(POLL_INTERVAL);

    loop {
        tokio::select! {
            _ = cancel.cancelled() => break,
            _ = ticker.tick() => {
                let cpu = match CpuSnap::now().await {
                    Some(now) => {
                        let usage = prev_cpu.map(|p| now.usage_since(p)).unwrap_or(0.0);
                        prev_cpu = Some(now);
                        usage.clamp(0.0, 1.0)
                    }
                    None => 0.0,
                };

                let gpu = match &gpu_path {
                    Some(p) => read_gpu(p).await.unwrap_or(0.0),
                    None => 0.0,
                };

                if tx.send(Load { cpu, gpu, gpu_available }).await.is_err() {
                    break;
                }
            }
        }
    }
}

// --- CPU Load ---

#[derive(Clone, Copy)]
struct CpuSnap {
    total: f64,
    busy: f64,
}

impl CpuSnap {
    async fn now() -> Option<Self> {
        let text = timeout(READ_TIMEOUT, fs::read_to_string("/proc/stat"))
            .await
            .ok()?
            .ok()?;

        let mut nums = text.lines().next()?.split_whitespace().skip(1);

        let user: f64 = nums.next()?.parse().ok()?;
        let nice: f64 = nums.next()?.parse().ok()?;
        let system: f64 = nums.next()?.parse().ok()?;
        let idle: f64 = nums.next()?.parse().ok()?;
        let iowait: f64 = nums.next()?.parse().ok()?;
        let irq: f64 = nums.next()?.parse().ok()?;
        let softirq: f64 = nums.next()?.parse().ok()?;
        let steal: f64 = nums.next().unwrap_or("0").parse().unwrap_or(0.0);

        Some(Self {
            total: user + nice + system + idle + iowait + irq + softirq + steal,
            busy: user + nice + system + irq + softirq + steal,
        })
    }

    fn usage_since(self, prev: Self) -> f64 {
        let dt = (self.total - prev.total).max(1.0);
        let db = (self.busy - prev.busy).max(0.0);
        db / dt
    }
}

// --- GPU Load ---

async fn find_gpu_path() -> Option<String> {
    if let Some(p) = sysfs::first_available(paths::GPU_LOAD_PATHS).await {
        return Some(p);
    }
    // Fallback glob for unlisted devfreq devices
    for path in sysfs::glob("/sys/class/devfreq/*/load").await {
        if fs::read_to_string(&path).await.is_ok() {
            return Some(path.to_string_lossy().into_owned());
        }
    }
    None
}

async fn read_gpu(path: &str) -> Option<f64> {
    let text = sysfs::read(path).await?;

    // Adreno gpubusy format: "busy total"
    if path.contains("gpubusy") {
        let parts: Vec<&str> = text.split_whitespace().collect();
        if parts.len() >= 2 {
            let busy: f64 = parts[0].parse().ok()?;
            let total: f64 = parts[1].parse().ok()?;
            return (total > 0.0).then(|| (busy / total).clamp(0.0, 1.0));
        }
        return None;
    }

    // Mali utilization format: "XX%" or "label: XX%"
    if path.contains("utilization") || path.contains("utilisation") {
        let clean = text
            .split(':')
            .next_back()
            .unwrap_or(&text)
            .trim()
            .trim_end_matches('%');
        return clean
            .parse::<f64>()
            .ok()
            .map(|v| (v / 100.0).clamp(0.0, 1.0));
    }

    // Percentage paths
    if path.contains("percentage") || path.contains("percent") {
        return text
            .parse::<f64>()
            .ok()
            .map(|v| (v / 100.0).clamp(0.0, 1.0));
    }

    // Load paths: 0-100 or 0-1000
    if path.contains("load") {
        let val: f64 = text.parse().ok()?;
        let scale = if val > 100.0 { 1000.0 } else { 100.0 };
        return Some((val / scale).clamp(0.0, 1.0));
    }

    text.parse::<f64>()
        .ok()
        .map(|v| (v / 100.0).clamp(0.0, 1.0))
}
