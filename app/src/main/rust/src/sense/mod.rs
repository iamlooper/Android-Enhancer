//! Device sensing - hardware profile and runtime load.

mod device;
mod touch;

pub use device::{init_profile, profile, set_battery_info, set_screen_info, Load, LoadSensor};
pub use touch::TouchSensor;
