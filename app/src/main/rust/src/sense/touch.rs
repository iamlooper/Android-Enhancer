//! Touch input sensing.

use crate::core::mode::Touch;
use crate::util::log::Log;
use evdev::{AbsoluteAxisCode, Device, EventType, KeyCode};
use std::os::fd::AsRawFd;
use std::path::PathBuf;
use std::time::{Duration, Instant};
use tokio::io::unix::AsyncFd;
use tokio::io::Interest;
use tokio::sync::mpsc;
use tokio_util::sync::CancellationToken;

/// Touch sensor with cancellation support.
pub struct TouchSensor {
    cancel: CancellationToken,
    rx: Option<mpsc::Receiver<Touch>>,
}

impl TouchSensor {
    pub async fn start(log: &Log) -> Self {
        let (tx, rx) = mpsc::channel(32);
        let cancel = CancellationToken::new();
        let cancel_clone = cancel.clone();
        let log = log.clone();

        tokio::spawn(async move {
            run_touch_sensor(cancel_clone, tx, log).await;
        });

        Self { cancel, rx: Some(rx) }
    }

    /// Take ownership of the receiver (can only be called once)
    pub fn take_receiver(&mut self) -> mpsc::Receiver<Touch> {
        self.rx.take().expect("TouchSensor::take_receiver called more than once")
    }

    /// Check if the receiver is still available.
    pub fn has_receiver(&self) -> bool {
        self.rx.is_some()
    }

    pub fn stop(self) {
        self.cancel.cancel();
    }
}

impl Drop for TouchSensor {
    fn drop(&mut self) {
        self.cancel.cancel();
    }
}

async fn run_touch_sensor(cancel: CancellationToken, tx: mpsc::Sender<Touch>, log: Log) {
    let log_clone = log.clone();
    let path = tokio::task::spawn_blocking(move || find_device(&log_clone))
        .await
        .ok()
        .flatten();

    let Some(path) = path else {
        log.warn("No touchscreen found");
        // Keep task alive but idle to avoid channel errors
        cancel.cancelled().await;
        return;
    };

    let device = match Device::open(&path) {
        Ok(d) => d,
        Err(e) => {
            log.warn(&format!("Can't open touchscreen: {e}"));
            return;
        }
    };

    // Wrap the device fd for async I/O
    let fd = device.as_raw_fd();
    let async_fd = match AsyncFd::with_interest(fd, Interest::READABLE) {
        Ok(afd) => afd,
        Err(e) => {
            log.warn(&format!("Can't create async fd: {e}"));
            return;
        }
    };

    // We need to keep the device alive since async_fd borrows its fd
    let mut device = device;

    let mut touching = false;
    let mut start = (0i32, 0i32);
    let mut pos = (0i32, 0i32);
    let mut start_time = Instant::now();

    loop {
        tokio::select! {
            _ = cancel.cancelled() => break,
            ready = async_fd.readable() => {
                let mut guard = match ready {
                    Ok(g) => g,
                    Err(_) => continue,
                };

                // Process all available events
                match device.fetch_events() {
                    Ok(events) => {
                        for ev in events {
                            use evdev::EventSummary::*;
                            match ev.destructure() {
                                // BTN_TOUCH: traditional single-touch or Type A MT
                                Key(_, KeyCode::BTN_TOUCH, v) => {
                                    if v == 1 && !touching {
                                        touching = true;
                                        start = pos;
                                        start_time = Instant::now();
                                        let _ = tx.send(Touch::Tap).await;
                                    } else if v == 0 && touching {
                                        emit_touch_end(&tx, start_time, start, pos).await;
                                        touching = false;
                                    }
                                }
                                // ABS_MT_TRACKING_ID: Type B MT protocol (-1 = lifted)
                                AbsoluteAxis(_, AbsoluteAxisCode::ABS_MT_TRACKING_ID, v) => {
                                    if v >= 0 && !touching {
                                        touching = true;
                                        start = pos;
                                        start_time = Instant::now();
                                        let _ = tx.send(Touch::Tap).await;
                                    } else if v == -1 && touching {
                                        emit_touch_end(&tx, start_time, start, pos).await;
                                        touching = false;
                                    }
                                }
                                // ABS_MT_TOUCH_MAJOR: Some devices use contact area (0 = lifted)
                                AbsoluteAxis(_, AbsoluteAxisCode::ABS_MT_TOUCH_MAJOR, v) => {
                                    if v > 0 && !touching {
                                        touching = true;
                                        start = pos;
                                        start_time = Instant::now();
                                        let _ = tx.send(Touch::Tap).await;
                                    } else if v == 0 && touching {
                                        emit_touch_end(&tx, start_time, start, pos).await;
                                        touching = false;
                                    }
                                }
                                // Position tracking
                                AbsoluteAxis(_, axis, v) => match axis {
                                    AbsoluteAxisCode::ABS_MT_POSITION_X | AbsoluteAxisCode::ABS_X => pos.0 = v,
                                    AbsoluteAxisCode::ABS_MT_POSITION_Y | AbsoluteAxisCode::ABS_Y => pos.1 = v,
                                    _ => {}
                                },
                                _ => {}
                            }
                        }
                    }
                    Err(e) if e.kind() == std::io::ErrorKind::WouldBlock => {}
                    Err(_) => {} // Silently ignore read errors
                }

                guard.clear_ready();
            }
        }
    }
}

/// Emit a classified touch event when gesture completes
async fn emit_touch_end(tx: &mpsc::Sender<Touch>, start_time: Instant, start: (i32, i32), end: (i32, i32)) {
    let gesture = classify(start_time.elapsed(), start, end);
    // Only emit end event if it's different from the initial Tap boost
    if gesture != Touch::Tap {
        let _ = tx.send(gesture).await;
    }
}

fn classify(dur: Duration, start: (i32, i32), end: (i32, i32)) -> Touch {
    let dx = (end.0 - start.0) as f64;
    let dy = (end.1 - start.1) as f64;
    let dist = (dx * dx + dy * dy).sqrt();
    let ms = dur.as_millis();

    // DPI-scaled thresholds: higher DPI = require more pixels for same gesture
    let dpi_scale = super::device::profile().dpi_scale();
    let swipe_threshold = 80.0 * dpi_scale;

    if ms >= 400 {
        Touch::Hold
    } else if dist > swipe_threshold {
        if ms < 200 { Touch::Swipe } else { Touch::Scroll }
    } else {
        Touch::Tap
    }
}

fn find_device(_log: &Log) -> Option<PathBuf> {
    let mut mt_device: Option<PathBuf> = None;
    let mut st_device: Option<PathBuf> = None;

    for path in glob::glob("/dev/input/event*").ok()?.flatten() {
        let Ok(dev) = Device::open(&path) else { continue };
        
        if !dev.supported_events().contains(EventType::ABSOLUTE) {
            continue;
        }
        
        // Check for multi-touch support (preferred)
        if let Some(abs) = dev.supported_absolute_axes()
            && abs.contains(AbsoluteAxisCode::ABS_MT_POSITION_X)
        {
            // Perfect match: MT with BTN_TOUCH
            if dev.supported_keys().is_some_and(|k| k.contains(KeyCode::BTN_TOUCH)) {
                return Some(path);
            }
            mt_device.get_or_insert(path.clone());
        }
        
        // Fallback: single-touch with BTN_TOUCH
        if dev.supported_keys().is_some_and(|k| k.contains(KeyCode::BTN_TOUCH)) {
            st_device.get_or_insert(path);
        }
    }

    mt_device.or(st_device)
}
