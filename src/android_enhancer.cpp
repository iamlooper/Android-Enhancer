#include <iostream>
#include <cstring>
#include <vector>
#include <unistd.h>

#include "utils/logger.hpp"
#include "utils/android_utils.hpp"
#include "utils/fs_utils.hpp"
#include "utils/shell_utils.hpp"
#include "utils/thread_process_utils.hpp"
#include "utils/vmtouch_utils.hpp"

// Set standard namespace.
using namespace std;

// Declare functions.
void configureKernel();
void configureIo();
void configureProcessThread();
void configureCmdServices();
void disableUnnecessaryServices();
void configureSysProps();
void configureDalvikProps();
void configureDex();
void configureDexSecondary();
void cleanJunk();
void configureMiui();
void enableMemoryTweak();
void disableMemoryTweak();
void executeAllTweaks();
void executeMainTweak();
void executeDalvikTweak();

void configureKernel() {
    // Disable dynamic scheduler parameters adjustment.
    FSUtils::mutateFile("/proc/sys/kernel/sched_tunable_scaling", "0");
            
    // Allow scheduler boosting on top-app tasks.
    FSUtils::mutateFile("/proc/sys/kernel/sched_min_task_util_for_boost", "0");
    FSUtils::mutateFile("/proc/sys/kernel/sched_min_task_util_for_colocation", "0");
    FSUtils::mutateFile("/dev/stune/top-app/schedtune.prefer_idle", "0");
    FSUtils::mutateFile("/dev/stune/top-app/schedtune.boost", "1");    

    // Turn off printk logging and scheduler stats.
    FSUtils::mutateFile("/proc/sys/kernel/printk", "0 0 0 0");
    FSUtils::mutateFile("/proc/sys/kernel/printk_devkmsg", "off");
    FSUtils::mutateFile("/proc/sys/kernel/sched_schedstats", "0");
   
    // Disable swap readahead for swap devices.
    FSUtils::mutateFile("/proc/sys/vm/page-cluster", "0");

    // Update /proc/stat every 60 sec to reduce jitter.
    FSUtils::mutateFile("/proc/sys/vm/stat_interval", "60");
    
    // Don't debug OOM events.
    FSUtils::mutateFile("/proc/sys/vm/oom_dump_tasks", "0");          
    
    // Reduce lease break time for quicker reassignment of leases.
    FSUtils::mutateFile("/proc/sys/fs/lease-break-time", "15");
       
    // Print completion message.   
    Logger(LogType::INFO) << "Applied kernel tweak" << endl;
}

void configureIo() {
    // Get I/O block devices paths.
    vector<string> paths = FSUtils::getPathsFromWp("/sys/block/*");

    // Iterate through the list of paths.
    for (const string& path: paths) {
        // Don't use any I/O scheduler.
        FSUtils::mutateFile(path + "/queue/scheduler", "none");
        
        // Disable I/O statistics, random I/O addition, and I/O polling.
        FSUtils::mutateFile(path + "/queue/iostats", "0");
        FSUtils::mutateFile(path + "/queue/add_random", "0");
        FSUtils::mutateFile(path + "/queue/io_poll", "0");
    }

    // Print completion message.   
    Logger(LogType::INFO) << "Tweaked I/O parameters" << endl;
}

void configureProcessThread() {
    // Initialize variable.
    vector<string> processes;

    // List of processes.
    processes = {
        "rcu_sched",
        "writeback",
        "system",
        "zygote",
        "system_server",
        "surfaceflinger",
        "netd"
    };
    
    // Loop through each process.
    for (const string& process: processes) {
        // Set the priority of the process to -20 (highest priority).
        ThreadProcessUtils::reniceProcess(process, -20);
    }    
       
    // List of processes.
    processes = {
        "com.android.systemui",
        "android.process.acore",
        AndroidUtils::getHomePkgName()
    };

    // Loop through each process.
    for (const string& process: processes) {
        // Set the priority of the process to -20 (highest priority).
        ThreadProcessUtils::reniceProcess(process, -20);
    }
       
    // Print completion message. 
    Logger(LogType::INFO) << "Optimized priority of system processes" << endl;
}


// Disable debugging, statistics, unnecessary background apps, phantom process killer, lock profiling (analysis tool), make changes to `DeviceConfig` flags persistent, enable IORAP readahead feature, improve FSTrim time interval and tweak `ActivityManager`.
void configureCmdServices() {
    // List of service commands of `cmd` to execute.
    vector<string> svcCmds = {
        "settings put system anr_debugging_mechanism 0",
        "looper_stats disable",
        "settings put global netstats_enabled 0",
        "power set-fixed-performance-mode-enabled true",
        "activity idle-maintenance",
        "thermalservice override-status 0",
        "settings put global settings_enable_monitor_phantom_procs false",
        "device_config put runtime_native_boot disable_lock_profiling true",
        "device_config set_sync_disabled_for_tests persistent",
        "device_config put runtime_native_boot iorap_readahead_enable true",
        "settings put global fstrim_mandatory_interval 3600",
        "device_config put activity_manager max_phantom_processes 2147483647",
        "device_config put activity_manager max_cached_processes 256",
        "device_config put activity_manager max_empty_time_millis 43200000"
    };

    // Iterate through the list of service commands.
    for (const string& svcCmd: svcCmds) {
        // Use ShellUtils::exec() function to execute shell command.
        ShellUtils::exec("cmd " + svcCmd);
    }

    // Print completion message.
    Logger(LogType::INFO) << "Configured cmd services" << endl;
}

// Stop unnecessary services.
void disableUnnecessaryServices() {
    // List of services to be killed.
    vector<string> services = {
        "traced",
        "statsd",
        "tcpdump",
        "cnss_diag",
        "ipacm-diag",
        "subsystem_ramdump",
        "charge_logger",
        "wlan_logging"
    };

    // Iterate through the list of services.
    for (const string& service: services) {
        // Use AndroidUtils::stopSystemService() & ThreadProcessUtils::killProcess() function to stop service.
        AndroidUtils::stopSystemService(service);
        ThreadProcessUtils::killProcess(service);
    }

    // Print completion message.   
    Logger(LogType::INFO) << "Disabled unnecessary services" << endl;
}

void configureSysProps() {
    // List of properties to be modified.
    vector<string> props = {
        "persist.sys.usap_pool_enabled true",
        "persist.device_config.runtime_native.usap_pool_enabled true",
        "ro.iorapd.enable true",
        "vidc.debug.level 0",
        "persist.radio.ramdump 0",
        "ro.statsd.enable false",
        "persist.debug.sf.statistics 0",
        "debug.mdpcomp.logs 0",
        "ro.lmk.debug false",
        "ro.lmk.log_stats false",
        "debug.sf.enable_egl_image_tracker 0",
        "persist.ims.disableDebugLogs 1",
        "persist.ims.disableADBLogs 1",
        "persist.ims.disableQXDMLogs 1",
        "persist.ims.disableIMSLogs 1"
    };

    // Iterate through the list of properties.
    for (const string& prop: props) {
        // Use ShellUtils::exec() function to execute shell command.
        ShellUtils::exec("setprop " + prop);
    }

    // Print completion message. 
    Logger(LogType::INFO) << "Tweaked system properties" << endl;
}

void configureDalvikProps() {
    // List of properties to be modified.
    vector<string> props = {
        "dalvik.vm.minidebuginfo false",
        "dalvik.vm.dex2oat-minidebuginfo false",
        "dalvik.vm.check-dex-sum false",
        "dalvik.vm.checkjni false",
        "dalvik.vm.verify-bytecode false",
        "dalvik.gc.type precise",
        "persist.bg.dexopt.enable false"
    };

    // Iterate through the list of properties.
    for (const string& prop: props) {
        // Use ShellUtils::exec() function to execute shell command.
        ShellUtils::exec("setprop " + prop);
    }

    // Print completion message.   
    Logger(LogType::INFO) << "Applied dalvik optimization props" << endl;
}

// Improve main DEX files.
void configureDex() {
    // Use ShellUtils::exec() function to execute shell command.
    ShellUtils::exec("cmd package compile -m speed-profile -a");

    // Print completion message.   
    Logger(LogType::INFO) << "Applied main DEX tweak" << endl;
}

// Improve secondary DEX files.
void configureDexSecondary() {
    // List of shell commands to execute.
    vector<string> cmds = {
        "pm compile -m speed-profile --secondary-dex -a",
        "pm reconcile-secondary-dex-files -a",
        "pm compile --compile-layouts -a"
    };

    // Iterate through the list of commands.
    for (const string& cmd: cmds) {
        // Use ShellUtils::exec() function to execute shell command.
        ShellUtils::exec(cmd);
    }

    // Print completion message.   
    Logger(LogType::INFO) << "Applied secondary DEX tweak" << endl;
}

void cleanJunk() {
    // List of items to be cleaned.   
    vector<string> items = {
        "/data/*.log",
        "/data/vendor/wlan_logs",
        "/data/*.txt",
        "/cache/*.apk",
        "/data/anr/*",
        "/data/backup/pending/*.tmp",
        "/data/cache/*",
        "/data/data/*.log",
        "/data/data/*.txt",
        "/data/log/*.log",
        "/data/log/*.txt",
        "/data/local/*.apk",
        "/data/local/*.log",
        "/data/local/*.txt",
        "/data/mlog/*",
        "/data/system/*.log",
        "/data/system/*.txt",
        "/data/system/dropbox/*",
        "/data/system/usagestats/*",
        "/data/tombstones/*",
        "/sdcard/LOST.DIR",
        "/sdcard/found000",
        "/sdcard/LazyList",
        "/sdcard/albumthumbs",
        "/sdcard/kunlun",
        "/sdcard/.CacheOfEUI",
        "/sdcard/.bstats",
        "/sdcard/.taobao",
        "/sdcard/Backucup",
        "/sdcard/MIUI/debug_log",
        "/sdcard/UnityAdsVideoCache",
        "/sdcard/*.log",
        "/sdcard/*.CHK",
        "/sdcard/duilite",
        "/sdcard/DkMiBrowserDemo",
        "/sdcard/.xlDownload"
    };

    // Iterate through the list of items.
    for (const string& item: items) {
        // Use FSUtils::removePath() function to remove the item.
        FSUtils::removePath(item);
    }

    // Print completion message.   
    Logger(LogType::INFO) << "Cleaned junk from system" << endl;
}

void configureMiui() {
    // List of apps to be disabled.
    vector<string> apps = {
        "com.miui.analytics",
        "com.miui.systemAdSolution"
    };

    // Iterate through the list of apps.
    for (const string& app: apps) {
        // Use ShellUtils::exec() function to execute shell command.
        ShellUtils::exec("pm disable " + app);
    }

    // Log information about disabled apps.
    Logger(LogType::INFO) << "Disabled unnecessary MIUI apps." << endl;

    // List of properties to be modified.
    vector<string> props = {
        "persist.sys.miui.sf_cores 6",
        "persist.sys.miui_animator_sched.bigcores 6-7",
        "persist.sys.enable_miui_booster 0"
    };

    // Iterate through the list of properties.
    for (const string& prop: props) {
        // Use ShellUtils::exec() function to execute shell command.
        ShellUtils::exec("setprop " + prop);
    }

    // Log information about tuned properties.
    Logger(LogType::INFO) << "Tuned MIUI props." << endl;

    // Kill MIUI booster service.
    ThreadProcessUtils::killProcess("miuibooster");

    // Log information about turned off services.
    Logger(LogType::INFO) << "Turned off unnecessary MIUI services" << endl;
}

// Preload important system objects into memory.
void enableMemoryTweak() {
    // Initialize variable.
    vector<string> objects;

    // List of objects.
    objects = {
        "libc.so",
        "libm.so",
        "libdl.so"
    };
    
    // Loop through each object.
    for (const string& object: objects) {
        // Preload the object from the specified paths.
        VMTouchUtils::preloadFull("obj", "/apex/com.android.runtime/lib/bionic/" + object);
        VMTouchUtils::preloadFull("obj", "/apex/com.android.runtime/lib64/bionic/" + object);
    }

    // List of objects.
    objects = {
        "libc++.so",
        "libandroid_runtime.so",
        "libandroid_servers.so",
        "libsurfaceflinger.so",
        "libgui.so",
        "libinputflinger.so",
        "libinputreader.so",        
        "libblas.so",
        "libpng.so",
        "libjpeg.so",
        "liblz4.so",
        "liblzma.so",
        "libz.so"
    };

    // Loop through each object.
    for (const string& object: objects) {
        // Preload the object from the specified paths.
        VMTouchUtils::preloadFull("obj", "/system/lib/" + object);
        VMTouchUtils::preloadFull("obj", "/system/lib64/" + object);
    }
       
    // Print completion message.  
    Logger(LogType::INFO) << "Preloaded important system items into RAM" << endl;
}

void disableMemoryTweak() {
    // Kill all `vmtouch` processes.
    ThreadProcessUtils::killProcess("vmtouch");

    // Print completion message.   
    Logger(LogType::INFO) << "Disabled memory preload tweak" << endl;
}

void executeAllTweaks() {
    Logger(LogType::INFO) << "[Start] Android Enhancer Tweaks" << endl;

    configureKernel();
    configureIo();
    configureProcessThread();
    configureCmdServices();
    disableUnnecessaryServices();
    configureSysProps();
    configureDalvikProps();
    configureDex();
    configureDexSecondary();
    cleanJunk();
    enableMemoryTweak();

    // Execute MIUI tweak if MIUI is installed.
    if (AndroidUtils::isAppExists("com.xiaomi.misettings")) {
        configureMiui();
    }

    Logger(LogType::INFO) << "[End] Android Enhancer Tweaks" << endl;
}

void executeMainTweak() {
    Logger(LogType::INFO) << "[Start] Main Tweak" << endl;

    configureKernel();
    configureIo();
    configureProcessThread();
    configureCmdServices();
    disableUnnecessaryServices();
    configureSysProps();

    Logger(LogType::INFO) << "[End] Main Tweak" << endl;
}

void executeDalvikTweak() {
    Logger(LogType::INFO) << "[Start] Dalvik Tweak" << endl;

    configureDalvikProps();
    configureDex();
    configureDexSecondary();

    Logger(LogType::INFO) << "[End] Dalvik Tweak" << endl;
}

int main(int argc, char * argv[]) {
    // Sync device.
    sync();

    // Get command line argument.
    if (argv[1] == nullptr) {
        Logger(LogType::ERROR) << "No command line argument provided" << endl;
        exit(1);
    }
    string flag = argv[1];

    // Check for matching flag then perform respective operation.
    if (flag == "--all-tweaks") {
        executeAllTweaks();
    } else if (flag == "--main-tweak") {
        executeMainTweak();
    } else if (flag == "--dalvik-tweak") {
        executeDalvikTweak();
    } else if (flag == "--clean-junk") {
        cleanJunk();
    } else if (flag == "--enable-memory-tweak") {
        enableMemoryTweak();
    } else if (flag == "--disable-memory-tweak") {
        disableMemoryTweak();
    } else {
        Logger(LogType::ERROR) << "No proper command line argument provided" << endl;
        exit(1);
    }

    return 0;
}