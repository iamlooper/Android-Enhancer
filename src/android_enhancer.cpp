#include <iostream>
#include <string>
#include <vector>
#include <unistd.h>
#include <fstream>
#include <getopt.h>
#include <sys/prctl.h>
#include <thread>

#include "util/logger.hpp"
#include "util/android_util.hpp"
#include "util/fs_util.hpp"
#include "util/shell_util.hpp"
#include "util/thread_process_util.hpp"
#include "util/cgroup_util.hpp"
#include "focused_app_opt.hpp"

struct DeviceSpecs {
    int totalRam;
    int cpuCores;
    int maxFreq;
};

struct SchedParams {
    int schedPeriod;
    int schedTasks;
};

DeviceSpecs deviceSpecs;

SchedParams schedParams;

DeviceSpecs getDeviceSpecs();

SchedParams calculateSchedParams(const DeviceSpecs &specs);

void initializeSchedParams();

void kernelTweak();

void vmTweak();

void ioTweak();

void priorityTweak();

void cmdTweak();

void unnecessaryServicesTweak();

void sysPropsTweak();

void packagesTweak();

void allTweaks();

void mainTweak();

void artTweak();

void logTrimmer(const std::string &fileName);

void printUsage();

DeviceSpecs getDeviceSpecs() {
    DeviceSpecs specs;
    specs.totalRam = std::stoi(
            ShellUtil::exec(
                    "grep MemTotal /proc/meminfo | cut -f2 -d':' | tr -d ' kB'").second);  // In KB
    specs.cpuCores = std::thread::hardware_concurrency();
    specs.maxFreq = std::stoi(
            FSUtil::readFile("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq"));
    return specs;
}

SchedParams calculateSchedParams(const DeviceSpecs &specs) {
    SchedParams params;
    int basePeriod = 20000000;
    int baseTasks = 10;
    int ramFactor = specs.totalRam / 1048576;  // Convert to GB
    int coreFactor = specs.cpuCores / 4;

    params.schedPeriod = basePeriod * coreFactor / ramFactor;
    params.schedTasks = baseTasks * ramFactor * coreFactor;

    params.schedPeriod = std::max(10000000, std::min(params.schedPeriod, 30000000));
    params.schedTasks = std::max(5, std::min(params.schedTasks, 20));

    return params;
}

void initializeSchedParams() {
    deviceSpecs = getDeviceSpecs();
    schedParams = calculateSchedParams(deviceSpecs);
}

void kernelTweak() {
    FSUtil::writeFile("/proc/sys/kernel/sched_tunable_scaling", "0");
    FSUtil::writeFile("/proc/sys/kernel/sched_min_task_util_for_boost", "0");
    FSUtil::writeFile("/proc/sys/kernel/sched_min_task_util_for_colocation", "0");
    FSUtil::writeFile("/dev/stune/top-app/schedtune.prefer_idle", "0");
    FSUtil::writeFile("/dev/stune/top-app/schedtune.boost", "1");
    FSUtil::writeFile("/proc/sys/kernel/printk", "0 0 0 0");
    FSUtil::writeFile("/proc/sys/kernel/printk_devkmsg", "off");
    FSUtil::writeFile("/proc/sys/kernel/sched_schedstats", "0");
    FSUtil::writeFile("/proc/sys/kernel/sched_latency_ns",
                       std::to_string(schedParams.schedPeriod));
    FSUtil::writeFile("/proc/sys/kernel/sched_min_granularity_ns",
                       std::to_string(schedParams.schedPeriod / schedParams.schedTasks));
    FSUtil::writeFile("/proc/sys/kernel/sched_wakeup_granularity_ns",
                       std::to_string(schedParams.schedPeriod / 2));
    FSUtil::writeFile("/proc/sys/kernel/sched_migration_cost_ns",
                       std::to_string(5000000 * deviceSpecs.cpuCores / 4));
    FSUtil::writeFile("/proc/sys/kernel/sched_nr_migrate",
                       std::to_string(32 * deviceSpecs.cpuCores / 4));
    FSUtil::writeFile("/proc/sys/kernel/sched_autogroup_enabled", "1");
    FSUtil::writeFile("/proc/sys/kernel/sched_child_runs_first", "1");
    FSUtil::writeFile("/proc/sys/kernel/perf_cpu_time_max_percent", "5");

    Logger(LogType::INFO) << "Completed kernel tweak" << std::endl;
}

void vmTweak() {
    FSUtil::writeFile("/proc/sys/vm/dirty_ratio", "30");
    FSUtil::writeFile("/proc/sys/vm/dirty_background_ratio", "10");
    FSUtil::writeFile("/proc/sys/vm/dirty_expire_centisecs", "3000");
    FSUtil::writeFile("/proc/sys/vm//proc/sys/vm/dirty_writeback_centisecs", "3000");
    FSUtil::writeFile("/proc/sys/vm/page-cluster", "0");
    FSUtil::writeFile("/proc/sys/vm/swappiness", "100");
    FSUtil::writeFile("/proc/sys/vm/vfs_cache_pressure", "100");
    FSUtil::writeFile("/proc/sys/vm/stat_interval", "10");

    Logger(LogType::INFO) << "Completed VM tweak" << std::endl;
}

void cpuTweak() {
    std::vector <std::string> paths = FSUtil::getPathsFromWp(
            "/sys/devices/system/cpu/cpu*/cpufreq");

    for (const std::string &path: paths) {
        std::string availableGovernors = FSUtil::readFile(path + "/scaling_available_governors");
        std::vector <std::string> governors = {"schedutil", "interactive"};

        for (const std::string &governor: governors) {
            if (availableGovernors.find(governor) != std::string::npos) {
                FSUtil::writeFile(path + "/scaling_governor", governor);

                if (governor == "schedutil") {
                    FSUtil::writeFile(path + "/schedutil/up_rate_limit_us",
                                       std::to_string(schedParams.schedPeriod / 1000));
                    FSUtil::writeFile(path + "/schedutil/down_rate_limit_us",
                                       std::to_string(4 * schedParams.schedPeriod / 1000));
                    FSUtil::writeFile(path + "/schedutil/rate_limit_us",
                                       std::to_string(schedParams.schedPeriod / 1000));
                    FSUtil::writeFile(path + "/schedutil/hispeed_load", "90");
                    FSUtil::writeFile(path + "/schedutil/hispeed_freq",
                                       std::to_string(deviceSpecs.maxFreq));
                } else if (governor == "interactive") {
                    FSUtil::writeFile(path + "/interactive/timer_rate",
                                       std::to_string(schedParams.schedPeriod / 1000));
                    FSUtil::writeFile(path + "/interactive/min_sample_time",
                                       std::to_string(schedParams.schedPeriod / 1000));
                    FSUtil::writeFile(path + "/interactive/go_hispeed_load", "90");
                    FSUtil::writeFile(path + "/interactive/hispeed_freq",
                                       std::to_string(deviceSpecs.maxFreq));
                }

                break;
            }
        }
    }

    Logger(LogType::INFO) << "Completed CPU tweak" << std::endl;
}

void ioTweak() {
    std::vector <std::string> paths = FSUtil::getPathsFromWp("/sys/block/*");

    for (const std::string &path: paths) {
        std::string availScheds = FSUtil::readFile(path + "/queue/scheduler");
        std::vector <std::string> preferredScheds = {"cfq", "kyber", "bfq", "mq-deadline", "noop",
                                                     "none"};

        for (const std::string &sched: preferredScheds) {
            if (availScheds.find(sched) != std::string::npos) {
                FSUtil::writeFile(path + "/queue/scheduler", sched);
                break;
            }
        }

        FSUtil::writeFile(path + "/queue/iostats", "0");
        FSUtil::writeFile(path + "/queue/add_random", "0");
        FSUtil::writeFile(path + "/queue/io_poll", "0");

        int readAheadKb = std::min(512, 128 * (deviceSpecs.totalRam / 1048576));
        FSUtil::writeFile(path + "/queue/read_ahead_kb", std::to_string(readAheadKb));
        FSUtil::writeFile(path + "/queue/nr_requests", "64");
    }

    Logger(LogType::INFO) << "Completed I/O tweak" << std::endl;
}

void priorityTweak() {
    CgroupUtil cgroupUtil;
    std::string imePkgName = AndroidUtil::getImePkgName();
    std::string homePkgName = AndroidUtil::getHomePkgName();

    cgroupUtil.pinProcOnPerf("system_server");
    cgroupUtil.changeTaskHighPrio("system_server");
    cgroupUtil.changeTaskRt("system_server", 1);

    cgroupUtil.pinProcOnPerf("zygote");
    cgroupUtil.pinProcOnPerf("zygote64");
    cgroupUtil.changeTaskHighPrio("zygote");
    cgroupUtil.changeTaskHighPrio("zygote64");

    cgroupUtil.pinProcOnPerf("surfaceflinger");
    cgroupUtil.changeTaskHighPrio("surfaceflinger");

    cgroupUtil.pinProcOnMid(imePkgName);
    cgroupUtil.changeTaskHighPrio(imePkgName);

    cgroupUtil.pinProcOnPerf("com.android.systemui");
    cgroupUtil.changeTaskHighPrio("com.android.systemui");

    cgroupUtil.pinProcOnPerf(homePkgName);
    cgroupUtil.changeTaskHighPrio(homePkgName);

    cgroupUtil.changeTaskNice("logd", 1);
    cgroupUtil.changeTaskNice("statsd", 5);
    cgroupUtil.changeTaskNice("tombstoned", 5);
    cgroupUtil.changeTaskNice("traced", 5);
    cgroupUtil.changeTaskNice("traced_probes", 5);

    cgroupUtil.pinProcOnPwr("logd");
    cgroupUtil.pinProcOnPwr("statsd");
    cgroupUtil.pinProcOnPwr("tombstoned");
    cgroupUtil.pinProcOnPwr("traced");
    cgroupUtil.pinProcOnPwr("traced_probes");

    Logger(LogType::INFO) << "Completed priority tweak" << std::endl;
}


void cmdTweak() {
    std::vector <std::string> svcCmds = {
            "settings put system anr_debugging_mechanism 0",
            "looper_stats disable",
            "settings put global netstats_enabled 0",
            "device_config put runtime_native_boot disable_lock_profiling true",
            "device_config put runtime_native_boot iorap_readahead_enable true",
            "settings put global fstrim_mandatory_interval 3600",
            "power set-fixed-performance-mode-enabled true",
            "activity idle-maintenance",
            "thermalservice override-status 0"
    };

    for (const std::string &svcCmd: svcCmds) {
        ShellUtil::exec("cmd " + svcCmd);
    }

    Logger(LogType::INFO) << "Completed CMD tweak" << std::endl;
}

// Stop unnecessary services
void unnecessaryServicesTweak() {
    std::vector <std::string> services = {
            "traced",
            "statsd",
            "tcpdump",
            "cnss_diag",
            "ipacm-diag",
            "subsystem_ramdump",
            "charge_logger",
            "wlan_logging"
    };

    for (const std::string &service: services) {
        ThreadProcessUtil::killProcess(service, true);
    }

    Logger(LogType::INFO) << "Completed unnecessary services tweak" << std::endl;
}

void sysPropsTweak() {
    std::vector <std::string> props = {
            // Common
            "persist.sys.usap_pool_enabled true",
            "persist.device_config.runtime_native.usap_pool_enabled true",
            "ro.iorapd.enable true",
            "vidc.debug.level 0",
            "vendor.vidc.debug.level 0",
            "vendor.swvdec.log.level 0",
            "persist.radio.ramdump 0",
            "persist.sys.lmk.reportkills false",
            "persist.vendor.dpm.loglevel 0",
            "persist.vendor.dpmhalservice.loglevel 0",
            "persist.debug.sf.statistics 0",
            "debug.sf.enable_egl_image_tracker 0",
            "debug.mdpcomp.logs 0",
            "persist.ims.disableDebugLogs 1",
            "persist.ims.disableADBLogs 1",
            "persist.ims.disableQXDMLogs 1",
            "persist.ims.disableIMSLogs 1",
            // Dalvik
            "dalvik.vm.minidebuginfo false",
            "dalvik.vm.dex2oat-minidebuginfo false",
            "dalvik.vm.check-dex-sum false",
            "dalvik.vm.checkjni false",
            "dalvik.vm.verify-bytecode false",
            "dalvik.gc.type generational_cc",
            "dalvik.vm.usejit false",
            "dalvik.vm.dex2oat-swap true",
            "dalvik.vm.dex2oat-resolve-startup-strings true",
            "dalvik.vm.systemservercompilerfilter speed-profile",
            "dalvik.vm.systemuicompilerfilter speed-profile",
            "dalvik.vm.usap_pool_enabled true"
    };

    for (const std::string &prop: props) {
        ShellUtil::exec("setprop " + prop);
    }

    Logger(LogType::INFO) << "Completed system properties tweak" << std::endl;
}

void packagesTweak() {
    std::vector <std::string> cmds = {
            // A13 and earlier
            "pm compile -m speed-profile -a",
            "pm compile -m speed-profile --secondary-dex -a",
            "pm compile --compile-layouts -a",
            // A14 and later
            "pm compile -m speed-profile --full -a",
            "pm art dexopt-packages -r bg-dexopt",
            "pm art cleanup"
    };

    for (const std::string &cmd: cmds) {
        ShellUtil::exec(cmd);
    }

    Logger(LogType::INFO) << "Completed packages tweak" << std::endl;
}

void allTweaks() {
    Logger(LogType::INFO) << "[Start] Android Enhancer Tweaks" << std::endl;

    kernelTweak();
    vmTweak();
    cpuTweak();
    ioTweak();
    priorityTweak();
    cmdTweak();
    unnecessaryServicesTweak();
    sysPropsTweak();
    packagesTweak();
    if (!FocusedAppOptimizer::isRunning()) {
        FocusedAppOptimizer::start();
    }

    Logger(LogType::INFO) << "[End] Android Enhancer Tweaks" << std::endl;
}

void mainTweak() {
    Logger(LogType::INFO) << "[Start] Main Tweak" << std::endl;

    kernelTweak();
    vmTweak();
    cpuTweak();
    ioTweak();
    cmdTweak();
    unnecessaryServicesTweak();
    sysPropsTweak();

    Logger(LogType::INFO) << "[End] Main Tweak" << std::endl;
}

void artTweak() {
    Logger(LogType::INFO) << "[Start] ART Tweak" << std::endl;

    sysPropsTweak();
    packagesTweak();

    Logger(LogType::INFO) << "[End] ART Tweak" << std::endl;
}

void logTrimmer(const std::string &fileName) {
    if (ShellUtil::exec("ps -Ao cmd | grep ae_lt | grep -v grep").first == 0) {
        return;
    }

    ThreadProcessUtil::daemonize([fileName]() {
        prctl(PR_SET_NAME, "ae_lt", 0, 0, 0);

        while (true) {
            struct stat logStat;
            if (stat(fileName.c_str(), &logStat) == 0) {
                if (logStat.st_size > 1 * 1024 * 1024) {  // 1MB in bytes
                    std::ofstream logFile(fileName,
                                          std::ios_base::trunc);
                    logFile.close();

                    Logger(LogType::INFO) << "Trimmed the log" << std::endl;
                }
            }

            std::this_thread::sleep_for(std::chrono::seconds(60));
        }
    }, true);
}

void printUsage() {
    std::cout << "Usage: program [OPTIONS]" << std::endl;
    std::cout << "Options:" << std::endl;
    std::cout << "  -a           All tweaks" << std::endl;
    std::cout << "  -m           Main tweak" << std::endl;
    std::cout << "  -p           Priority tweak" << std::endl;
    std::cout << "  -r           ART tweak" << std::endl;
    std::cout << "  -f           Focused app optimizer" << std::endl;
    std::cout << "  -s           Get focused app optimizer status" << std::endl;
    std::cout << "  -o FILE      Specify output file for logging" << std::endl;
    std::cout << "  -h           Display this help message" << std::endl;
}

int main(int argc, char *argv[]) {
    sync();

    if (argc == 1) {
        printUsage();
        return 1;
    }

    initializeSchedParams();

    int c;
    std::string outputFileName;
    std::ofstream outputFile;
    bool all_tweaks = false;
    bool log_trimmer = false;

    // Reset getopt
    optind = 1;

    // Process all options in a single pass
    while ((c = getopt(argc, argv, "amprfso:h")) != -1) {
        switch (c) {
            case 's':
                if (argc != 2) {
                    std::cerr << "-s option must be used alone" << std::endl;
                    return 1;
                }
                return FocusedAppOptimizer::isRunning() ? 0 : 1;
            case 'a':
                all_tweaks = true;
                break;
            case 'm':
                mainTweak();
                break;
            case 'p':
                priorityTweak();
                break;
            case 'r':
                artTweak();
                break;
            case 'f':
                if (FocusedAppOptimizer::isRunning()) {
                    FocusedAppOptimizer::stop();
                } else {
                    FocusedAppOptimizer::start();
                }
                break;
            case 'o':
                outputFileName = optarg;
                log_trimmer = true;
                outputFile.open(outputFileName, std::ios_base::app);
                if (!outputFile.is_open()) {
                    std::cerr << "Unable to open output file " << outputFileName << std::endl;
                    return 1;
                }
                std::cout.rdbuf(outputFile.rdbuf());
                break;
            case 'h':
                printUsage();
                return 0;
            case '?':
                return 1;
            default:
                break;
        }
    }

    if (log_trimmer) {
        logTrimmer(outputFileName);
    }

    if (all_tweaks) {
        allTweaks();
    }

    if (outputFile.is_open()) {
        outputFile.close();
    }

    return 0;
}