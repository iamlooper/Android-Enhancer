//! Power modes and their behaviors.

/// Power mode selection.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, Default)]
pub enum Mode {
    /// Automatically switches between modes based on system load.
    Auto = 0,
    /// Prioritizes battery life over performance.
    Powersaver = 1,
    /// Balanced between battery and performance.
    #[default]
    Balanced = 2,
    /// Maximum performance, ignores battery.
    Performance = 3,
    /// Gaming mode - extreme performance.
    Gaming = 4,
}

impl Mode {
    pub fn from_code(code: u32) -> Self {
        match code {
            1 => Self::Powersaver,
            2 => Self::Balanced,
            3 => Self::Performance,
            4 => Self::Gaming,
            _ => Self::Auto,
        }
    }

    pub fn code(self) -> u32 {
        self as u32
    }

    pub fn name(self) -> &'static str {
        match self {
            Self::Auto => "Auto",
            Self::Powersaver => "Powersaver",
            Self::Balanced => "Balanced",
            Self::Performance => "Performance",
            Self::Gaming => "Gaming",
        }
    }

    /// Intensity scale for tuning: 0.0 (max battery) to 1.0 (max performance).
    /// This is the unified scale used by all tune modules.
    pub fn intensity(self) -> f64 {
        match self {
            Self::Powersaver => 0.0,
            Self::Auto | Self::Balanced => 0.35,
            Self::Performance => 0.75,
            Self::Gaming => 1.0,
        }
    }

    /// Human description of this mode.
    pub fn describe(self) -> &'static str {
        match self {
            Self::Auto => "adapting to what you do",
            Self::Powersaver => "saving battery",
            Self::Balanced => "balanced speed and battery",
            Self::Performance => "maximum speed",
            Self::Gaming => "extreme gaming performance",
        }
    }

    /// Can this mode auto-switch to other modes?
    pub fn is_auto(self) -> bool {
        self == Self::Auto
    }
}

/// User touch action that triggers temporary boosts.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Touch {
    Tap,
    Swipe,
    Scroll,
    Hold,
}

/// What type of boost is needed based on current workload.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum BoostType {
    /// Only CPU needs boosting (low GPU load)
    CpuOnly,
    /// Only GPU needs boosting (low CPU load, high GPU)
    GpuOnly,
    /// Both CPU and GPU need boosting
    Both,
    /// No boost needed (system is idle)
    None,
}

impl Touch {
    pub fn name(self) -> &'static str {
        match self {
            Self::Tap => "Tapped",
            Self::Swipe => "Swiped", 
            Self::Scroll => "Scrolled",
            Self::Hold => "Held",
        }
    }

    /// Extra boost this action deserves (0.0 - 1.0).
    pub fn boost(self) -> f64 {
        match self {
            Self::Tap => 0.25,
            Self::Swipe => 0.45,
            Self::Scroll => 0.65,
            Self::Hold => 0.15,
        }
    }
}
