//! Sysfs utilities with caching, auto-backup, disk persistence, and timeouts.
//!
//! - Cache prevents redundant writes (zero I/O on cache hit)
//! - Auto-backup captures original values before first write
//! - Persists backups to disk for recovery across app updates/force-stops
//! - Writes with timeout prevent kernel hangs

use crate::tune;
use dashmap::DashMap;
use glob::glob as glob_sync;
use once_cell::sync::Lazy;
use parking_lot::RwLock;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, Ordering};
use std::time::Duration;
use tokio::fs::{self, OpenOptions};
use tokio::io::AsyncWriteExt;
use tokio::time::timeout;

/// Timeout for sysfs write operations
const WRITE_TIMEOUT: Duration = Duration::from_millis(100);

/// Timeout for sysfs read operations
const READ_TIMEOUT: Duration = Duration::from_millis(50);

/// Global sysfs state: cache + backup + disk path
static STATE: Lazy<SysfsState> = Lazy::new(SysfsState::new);

/// Flag to track if backup has pending changes that need disk sync
static BACKUP_DIRTY: AtomicBool = AtomicBool::new(false);

struct SysfsState {
    cache: DashMap<String, String>,
    backup: DashMap<String, String>,
    /// Path to disk backup file
    backup_path: RwLock<Option<PathBuf>>,
}

impl SysfsState {
    fn new() -> Self {
        Self {
            cache: DashMap::with_capacity(256),
            backup: DashMap::with_capacity(256),
            backup_path: RwLock::new(None),
        }
    }
}

/// Disk backup format
#[derive(Serialize, Deserialize, Default)]
struct BackupFile {
    /// true while running, false after clean shutdown
    active: bool,
    /// Sysfs path -> original value
    values: HashMap<String, String>,
}

/// Initialize backup system with disk path.
///
/// If a backup file exists with active=true, loads it into memory and restores values
/// (indicates previous session didn't stop cleanly due to update/force-stop).
pub async fn init_backup(path: Option<&str>) {
    // Set the backup path
    {
        let mut bp = STATE.backup_path.write();
        *bp = path.map(PathBuf::from);
    }

    // Check if backup file exists with active=true (previous session didn't stop cleanly)
    if let Some(backup_path) = path
        && let Some(backup) = load_backup_file(backup_path).await
        && backup.active && !backup.values.is_empty()
    {
        // Load disk backup into memory first (fast)
        for (sysfs_path, value) in backup.values {
            if !value.is_empty() {
                STATE.backup.insert(sysfs_path, value);
            }
        }
        
        // Restore from memory (uses existing restore_all logic)
        restore_all().await;
    }
}

/// Sync pending backup to disk.
pub async fn sync_backup() {
    if !BACKUP_DIRTY.swap(false, Ordering::Relaxed) {
        return; // Nothing to sync
    }

    let backup_path = {
        let bp = STATE.backup_path.read();
        match bp.as_ref() {
            Some(p) => p.clone(),
            None => return,
        }
    };

    // Collect all backup entries
    let values: HashMap<String, String> = STATE.backup
        .iter()
        .map(|e| (e.key().clone(), e.value().clone()))
        .collect();

    if values.is_empty() {
        let _ = fs::remove_file(&backup_path).await;
        return;
    }

    let backup = BackupFile {
        active: true,
        values,
    };

    let json = match serde_json::to_string(&backup) {
        Ok(j) => j,
        Err(_) => return,
    };

    let _ = timeout(WRITE_TIMEOUT * 10, async {
        let mut file = OpenOptions::new()
            .write(true)
            .create(true)
            .truncate(true)
            .open(&backup_path)
            .await?;
        file.write_all(json.as_bytes()).await?;
        file.flush().await
    }).await;
}

/// Load backup file from disk.
async fn load_backup_file(path: &str) -> Option<BackupFile> {
    let content = timeout(READ_TIMEOUT * 10, fs::read_to_string(path))
        .await
        .ok()?
        .ok()?;
    serde_json::from_str(&content).ok()
}

/// Read a sysfs path asynchronously with timeout.
pub async fn read(path: &str) -> Option<String> {
    timeout(READ_TIMEOUT, fs::read_to_string(path))
        .await
        .ok()?
        .ok()
        .map(|s| s.trim().to_string())
}

/// Read and parse to i64.
pub async fn read_i64(path: &str) -> Option<i64> {
    read(path).await?.parse().ok()
}

/// Write to sysfs with caching, auto-backup, and timeout.
/// - Skips write if cached value matches (no I/O)
/// - Backs up original value before first write to path
/// - Marks backup as dirty (call sync_backup to persist)
/// - Returns true if write was performed.
pub async fn write(path: &str, value: &str) -> bool {
    // Fast path: check cache (no I/O)
    if STATE.cache.get(path).is_some_and(|c| c.value() == value) {
        return false;
    }

    // Auto-backup: save original value before first write
    if !STATE.backup.contains_key(path) {
        let original = read(path).await.unwrap_or_default();
        STATE.backup.insert(path.to_string(), original);
        BACKUP_DIRTY.store(true, Ordering::Relaxed);
    }

    // Perform write with timeout
    if write_raw(path, value).await {
        STATE.cache.insert(path.to_string(), value.to_string());
        true
    } else {
        false
    }
}

/// Write multiple paths matching a glob pattern.
pub async fn write_many(pattern: &str, value: &str) {
    let paths = glob(pattern).await;
    for path in paths {
        write(&path.to_string_lossy(), value).await;
    }
}

/// Restore all backed up values to their original state.
pub async fn restore_all() -> usize {
    // Clone entries to avoid holding DashMap locks during I/O
    let entries: Vec<(String, String)> = STATE.backup
        .iter()
        .map(|e| (e.key().clone(), e.value().clone()))
        .collect();
    
    if entries.is_empty() {
        return 0;
    }

    let mut restored = 0;
    for (path, value) in entries {
        if !value.is_empty() {
            write_raw(&path, &value).await;
            restored += 1;
        }
    }
    STATE.backup.clear();
    STATE.cache.clear();

    // Mark backup as inactive (clean shutdown)
    let backup_path = {
        let bp = STATE.backup_path.read();
        bp.as_ref().cloned()
    };
    if let Some(path) = backup_path {
        let backup = BackupFile {
            active: false,
            values: HashMap::new(),
        };
        if let Ok(json) = serde_json::to_string(&backup) {
            let _ = timeout(WRITE_TIMEOUT * 10, async {
                let mut file = OpenOptions::new()
                    .write(true)
                    .create(true)
                    .truncate(true)
                    .open(&path)
                    .await?;
                file.write_all(json.as_bytes()).await?;
                file.flush().await
            }).await;
        }
    }

    // Reset first-apply flag for next engine start
    tune::reset_first_apply();

    restored
}

/// Find first available path from list.
pub async fn first_available(paths: &[&str]) -> Option<String> {
    for p in paths {
        if fs::try_exists(p).await.unwrap_or(false) {
            return Some(p.to_string());
        }
    }
    None
}

/// Check if path exists.
pub async fn exists(path: &str) -> bool {
    fs::try_exists(path).await.unwrap_or(false)
}

/// Glob for paths asynchronously (offloads to blocking threadpool).
pub async fn glob(pattern: &str) -> Vec<PathBuf> {
    let pattern = pattern.to_string();
    tokio::task::spawn_blocking(move || {
        glob_sync(&pattern)
            .map(|paths| paths.filter_map(Result::ok).collect())
            .unwrap_or_default()
    })
    .await
    .unwrap_or_default()
}

/// Raw write with timeout.
async fn write_raw(path: &str, value: &str) -> bool {
    let result = timeout(WRITE_TIMEOUT, async {
        let mut file = OpenOptions::new()
            .write(true)
            .open(path)
            .await?;
        file.write_all(value.as_bytes()).await
    })
    .await;

    matches!(result, Ok(Ok(())))
}
