//! Logging to file.

use parking_lot::RwLock;
use std::path::PathBuf;
use std::sync::atomic::{AtomicU32, Ordering};
use std::sync::Arc;
use tokio::fs::{File, OpenOptions};
use tokio::io::AsyncWriteExt;
use tokio::runtime::Handle;
use tokio::sync::Mutex;

static RUNTIME_HANDLE: RwLock<Option<Handle>> = RwLock::new(None);

/// Log rotation threshold: check size after this many entries
const ROTATION_CHECK_INTERVAL: u32 = 200;

/// Maximum log file size before rotation (512 KB)
const MAX_LOG_SIZE_BYTES: u64 = 512 * 1024;

/// Set the tokio runtime handle for async file logging.
/// Can be called multiple times to update the handle.
pub fn set_runtime_handle(handle: Handle) {
    *RUNTIME_HANDLE.write() = Some(handle);
}

/// Logger that writes to a file.
#[derive(Clone)]
pub struct Log {
    inner: Arc<Inner>,
}

struct Inner {
    file: Mutex<Option<File>>,
    path: Mutex<Option<PathBuf>>,
    /// Entry counter for automatic log rotation
    entry_count: AtomicU32,
}

impl Log {
    pub fn new(path: Option<&str>) -> Self {
        Self {
            inner: Arc::new(Inner {
                file: Mutex::new(None),
                path: Mutex::new(path.map(PathBuf::from)),
                entry_count: AtomicU32::new(0),
            }),
        }
    }

    /// Initialize the log file asynchronously. Call this after tokio runtime is available.
    pub async fn init_file(&self) {
        let path = self.inner.path.lock().await;
        if let Some(p) = &*path {
            let p_str = p.to_string_lossy().to_string();
            drop(path);
            *self.inner.file.lock().await = open(&p_str, false).await;
        }
    }

    pub async fn set_path(&self, path: Option<PathBuf>) {
        *self.inner.path.lock().await = path.clone();
        *self.inner.file.lock().await = match path {
            Some(p) => open(&p.to_string_lossy(), false).await,
            None => None,
        };
    }

    /// Log a normal message.
    pub fn say(&self, msg: &str) {
        self.spawn_append("•", msg);
    }

    /// Log a warning.
    pub fn warn(&self, msg: &str) {
        self.spawn_append("!", msg);
    }

    /// Log an error.
    #[allow(dead_code)]
    pub fn fail(&self, msg: &str) {
        self.spawn_append("✗", msg);
    }

    /// Clear the log file (async, for use with block_on).
    pub async fn clear_internal(&self) {
        let path = self.inner.path.lock().await;
        if let Some(p) = &*path {
            let p_str = p.to_string_lossy().to_string();
            drop(path);
            *self.inner.file.lock().await = open(&p_str, true).await;
        }
    }

    /// Clear the log file (spawns task, for sync contexts).
    pub fn clear(&self) {
        let Some(handle) = RUNTIME_HANDLE.read().clone() else {
            return;
        };
        let log = self.clone();
        handle.spawn(async move { log.clear_internal().await; });
    }

    fn spawn_append(&self, tag: &str, msg: &str) {
        let Some(handle) = RUNTIME_HANDLE.read().clone() else {
            // Runtime not available, skip file logging
            return;
        };

        let inner = self.inner.clone();
        let tag = tag.to_string();
        let msg = msg.to_string();
        let time = chrono::Local::now().format("%H:%M:%S").to_string();

        // Increment entry counter and check if rotation is needed
        let count = inner.entry_count.fetch_add(1, Ordering::Relaxed) + 1;
        let should_check_rotation = count >= ROTATION_CHECK_INTERVAL;
        if should_check_rotation {
            inner.entry_count.store(0, Ordering::Relaxed);
        }

        handle.spawn(async move {
            let mut guard = inner.file.lock().await;
            if let Some(f) = &mut *guard {
                let line = format!("{time} [{tag}] {msg}\n");
                let _ = f.write_all(line.as_bytes()).await;

                // Automatic rotation check based on entry count
                if should_check_rotation {
                    drop(guard); // Release lock before rotation
                    let path = inner.path.lock().await;
                    if let Some(p) = &*path
                        && let Ok(meta) = tokio::fs::metadata(p).await
                        && meta.len() > MAX_LOG_SIZE_BYTES
                    {
                        let p_str = p.to_string_lossy().to_string();
                        drop(path);
                        *inner.file.lock().await = open(&p_str, true).await;
                    }
                }
            }
        });
    }
}

async fn open(path: &str, truncate: bool) -> Option<File> {
    let p = std::path::Path::new(path);
    if let Some(parent) = p.parent() {
        let _ = tokio::fs::create_dir_all(parent).await;
    }

    OpenOptions::new()
        .create(true)
        .write(true)
        .truncate(truncate)
        .append(!truncate)
        .open(p)
        .await
        .ok()
}
