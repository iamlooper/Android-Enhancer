//! Android Enhancer - JNI Bridge
//!
//! The JNI bridge remains synchronous (required by JNI spec) but uses
//! block_on to interact with the async Engine internals.

mod core;
mod sense;
mod tune;
mod util;

use crate::core::engine::Engine;
use crate::core::mode::Mode;
use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jint, JNI_FALSE, JNI_TRUE};
use jni::JNIEnv;
use once_cell::sync::Lazy;
use parking_lot::Mutex;
use std::sync::atomic::{AtomicU32, Ordering};

static ENGINE: Lazy<Mutex<Engine>> = Lazy::new(|| Mutex::new(Engine::new()));
static ACTIVE_MODE: AtomicU32 = AtomicU32::new(0); // Auto

fn set_active(code: u32) {
    ACTIVE_MODE.store(code, Ordering::Relaxed);
}

// JNI Bridge - all functions remain synchronous as required by JNI

#[unsafe(no_mangle)]
pub extern "C" fn Java_io_github_iamlooper_androidenhancer_system_jni_JniBridge_start(
    mut env: JNIEnv,
    _class: JClass,
    log_path: JString,
    sysfs_backup_path: JString,
) -> jboolean {
    let log_path = env
        .get_string(&log_path)
        .ok()
        .map(|s| s.to_string_lossy().to_string());
    let sysfs_backup_path = env
        .get_string(&sysfs_backup_path)
        .ok()
        .map(|s| s.to_string_lossy().to_string());
    if ENGINE.lock().start(log_path, sysfs_backup_path) {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_io_github_iamlooper_androidenhancer_system_jni_JniBridge_stop(
    _env: JNIEnv,
    _class: JClass,
) {
    ENGINE.lock().stop();
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_io_github_iamlooper_androidenhancer_system_jni_JniBridge_clearLog(
    _env: JNIEnv,
    _class: JClass,
) {
    ENGINE.lock().clear_log();
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_io_github_iamlooper_androidenhancer_system_jni_JniBridge_isRunning(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    if ENGINE.lock().is_running() {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_io_github_iamlooper_androidenhancer_system_jni_JniBridge_getMode(
    _env: JNIEnv,
    _class: JClass,
) -> jint {
    ACTIVE_MODE.load(Ordering::Relaxed) as jint
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_io_github_iamlooper_androidenhancer_system_jni_JniBridge_setMode(
    _env: JNIEnv,
    _class: JClass,
    mode: jint,
) -> jint {
    ENGINE
        .lock()
        .set_mode(Mode::from_code(mode as u32), set_active);
    0
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_io_github_iamlooper_androidenhancer_system_jni_JniBridge_pushForegroundApp(
    mut env: JNIEnv,
    _class: JClass,
    package: JString,
) {
    let pkg = env
        .get_string(&package)
        .ok()
        .map(|s| s.to_string_lossy().to_string())
        .filter(|s| !s.is_empty());
    ENGINE.lock().push_app(pkg);
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_io_github_iamlooper_androidenhancer_system_jni_JniBridge_setAppOverride(
    mut env: JNIEnv,
    _class: JClass,
    package: JString,
    mode_code: jint,
) {
    let pkg = match env.get_string(&package) {
        Ok(s) => s.to_string_lossy().to_string(),
        Err(_) => return,
    };
    if pkg.is_empty() {
        return;
    }
    ENGINE
        .lock()
        .set_app_mode(pkg, Mode::from_code(mode_code as u32));
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_io_github_iamlooper_androidenhancer_system_jni_JniBridge_removeAppOverride(
    mut env: JNIEnv,
    _class: JClass,
    package: JString,
) {
    let pkg = match env.get_string(&package) {
        Ok(s) => s.to_string_lossy().to_string(),
        Err(_) => return,
    };
    if pkg.is_empty() {
        return;
    }
    ENGINE.lock().remove_app_mode(pkg);
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_io_github_iamlooper_androidenhancer_system_jni_JniBridge_setScreenState(
    _env: JNIEnv,
    _class: JClass,
    is_on: jboolean,
) {
    ENGINE.lock().set_screen_state(is_on != 0);
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_io_github_iamlooper_androidenhancer_system_jni_JniBridge_setBatteryInfo(
    _env: JNIEnv,
    _class: JClass,
    level: jint,
    capacity_mah: jint,
    is_charging: jboolean,
) {
    sense::set_battery_info(level, capacity_mah, is_charging != 0);
    ENGINE.lock().set_battery_level(level);
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_io_github_iamlooper_androidenhancer_system_jni_JniBridge_setScreenInfo(
    _env: JNIEnv,
    _class: JClass,
    dpi: jint,
    width_px: jint,
    height_px: jint,
) {
    sense::set_screen_info(dpi, width_px, height_px);
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_io_github_iamlooper_androidenhancer_system_jni_JniBridge_setTouchBoostEnabled(
    _env: JNIEnv,
    _class: JClass,
    enabled: jboolean,
) {
    ENGINE.lock().set_touch_boost_enabled(enabled != 0);
}

#[unsafe(no_mangle)]
pub extern "C" fn Java_io_github_iamlooper_androidenhancer_system_jni_JniBridge_isTouchBoostEnabled(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    if ENGINE.lock().is_touch_boost_enabled() {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}
