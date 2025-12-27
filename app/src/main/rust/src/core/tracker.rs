//! Load tracker and Auto mode logic.
//!
//! Tracks CPU/GPU load patterns and suggests appropriate power modes.

use crate::core::mode::{Mode, Touch};
use crate::sense::Load;

/// Smoothed load tracking with GPU fallback and mode suggestion.
pub struct Tracker {
    cpu_fast: f64,
    cpu_slow: f64,
    gpu: f64,
    burst: f64,
    idle_ticks: u32,
    busy_ticks: u32,
    gpu_ticks: u32,
    gpu_available: bool,
    pub load_variance: f64,
}

impl Tracker {
    pub fn new() -> Self {
        Self {
            cpu_fast: 0.0,
            cpu_slow: 0.0,
            gpu: 0.0,
            burst: 0.0,
            idle_ticks: 0,
            busy_ticks: 0,
            gpu_ticks: 0,
            gpu_available: false,
            load_variance: 0.0,
        }
    }

    /// Update tracker with new load sample.
    pub fn observe(&mut self, load: &Load) {
        self.gpu_available = load.gpu_available;

        let prev_cpu = self.cpu_slow;
        
        self.cpu_fast = blend(load.cpu, self.cpu_fast, 0.5);
        self.cpu_slow = blend(load.cpu, self.cpu_slow, 0.3);
        self.gpu = blend(load.gpu, self.gpu, 0.4);
        self.burst = blend((self.cpu_fast - self.cpu_slow).abs() * 1.5, self.burst, 0.25);

        // Load variance for dynamic rate limiting
        let diff = (load.cpu - prev_cpu).abs();
        self.load_variance = blend(diff * diff, self.load_variance, 0.2);

        let idle = self.cpu_fast < 0.1 && (self.gpu < 0.1 || !self.gpu_available);
        let busy = self.cpu_slow > 0.7 || (self.gpu_available && self.gpu > 0.8);

        // Gaming detection: sustained GPU + CPU activity
        if self.gpu_available && self.gpu > 0.5 && self.cpu_slow > 0.2 {
            self.gpu_ticks = self.gpu_ticks.saturating_add(1);
        } else {
            self.gpu_ticks = self.gpu_ticks.saturating_sub(1);
        }

        if busy {
            self.busy_ticks = self.busy_ticks.saturating_add(1);
            self.idle_ticks = 0;
        } else if idle {
            self.idle_ticks = self.idle_ticks.saturating_add(1);
            self.busy_ticks = 0;
        } else {
            self.idle_ticks = 0;
            self.busy_ticks = self.busy_ticks.saturating_sub(1);
        }
    }

    /// Register touch event to boost burst score.
    pub fn touch(&mut self, t: Touch) {
        self.burst = (self.burst + t.boost()).min(1.0);
        self.idle_ticks = 0;
    }

    /// Suggest a mode based on current load patterns.
    pub fn suggest(&self) -> Mode {
        // Weight redistribution when GPU unavailable
        let (cpu_w, gpu_w, burst_w) = if self.gpu_available {
            (0.50, 0.35, 0.15)
        } else {
            (0.70, 0.00, 0.30)
        };
        let score = self.cpu_slow * cpu_w + self.gpu * gpu_w + self.burst * burst_w;

        // Gaming: sustained GPU activity
        if self.gpu_available && self.gpu_ticks >= 5 && self.gpu > 0.5 {
            Mode::Gaming
        } else if score > 0.7 || self.busy_ticks >= 2 {
            Mode::Performance
        } else if score > 0.4 || (self.gpu_available && self.gpu > 0.4) {
            Mode::Balanced
        } else if self.idle_ticks >= 3 {
            Mode::Powersaver
        } else {
            Mode::Balanced
        }
    }

    /// Minimum interval (ms) before switching modes.
    ///
    /// Scales with mode intensity:
    /// - Powersave: longer interval (120ms base) for stability
    /// - Gaming: shorter interval (50ms base) for responsiveness
    ///
    /// High load variance increases interval to prevent thrashing.
    pub fn mode_switch_interval_ms(&self, intensity: f64) -> u64 {
        // Base interval: 120ms at i=0, 50ms at i=1
        let base_ms = 120.0 - intensity * 70.0;
        
        // Variance factor: 1.0 to 3.0 (higher variance = longer interval)
        let variance_factor = (1.0 + self.load_variance * 3.0).min(3.0);
        
        // Minimum 50ms to prevent rapid sysfs writes
        (base_ms * variance_factor).max(50.0).round() as u64
    }

    /// Number of consistent samples required before mode switch.
    ///
    /// Implements hysteresis to prevent mode oscillation:
    /// - Upgrading (more power): fewer samples needed (quick response)
    /// - Downgrading (less power): more samples needed (stable before drop)
    ///
    /// Higher intensity = faster decisions. Higher variance = more caution.
    pub fn stability_threshold(&self, intensity: f64, upgrading: bool) -> usize {
        // Base: 3-4 samples for upgrade, 8-10 for downgrade
        let base = if upgrading { 3.5 } else { 9.0 };
        
        // Intensity factor: 1.2x at i=0 (cautious) to 0.8x at i=1 (responsive)
        let intensity_factor = 1.2 - intensity * 0.4;
        
        // Variance factor: 1.0 to 2.5 (more samples when load is erratic)
        let variance_factor = 1.0 + self.load_variance * 1.5;
        
        (base * intensity_factor * variance_factor).round().max(2.0) as usize
    }
}

impl Default for Tracker {
    fn default() -> Self { Self::new() }
}

fn blend(new: f64, old: f64, alpha: f64) -> f64 {
    alpha * new + (1.0 - alpha) * old
}

