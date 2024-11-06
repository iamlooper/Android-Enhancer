#include "focused_app_opt.hpp"

#include <thread>
#include <chrono>
#include <csignal>

#include "util/shell_util.hpp"
#include "util/logger.hpp"
#include "util/thread_process_util.hpp"
#include "util/android_util.hpp"

FocusedAppOptimizer *_focusedAppOptimizer = nullptr;

void signalHandler(int signum) {
    if (_focusedAppOptimizer) {
        _focusedAppOptimizer->cleanup();
    }
    exit(signum);
}

FocusedAppOptimizer::FocusedAppOptimizer() : current_focused_app("") {
    _focusedAppOptimizer = this;
}

void FocusedAppOptimizer::cleanup() {
    if (!current_focused_app.empty()) {
        // Revert the current focused app to default state
        cgroupUtil.changeTaskNice(current_focused_app, 0);
        cgroupUtil.unpinProc(current_focused_app);
        Logger(LogType::INFO) << "Reverted current focused app " << current_focused_app
                              << " to original state" << std::endl;
    }
}

bool FocusedAppOptimizer::isRunning() {
    return ShellUtil::exec("ps -Ao cmd | grep ae_fao | grep -v grep").first == 0;
}

void FocusedAppOptimizer::start() {
    ThreadProcessUtil::daemonize([]() {
        prctl(PR_SET_NAME, "ae_fao", 0, 0, 0);

        std::signal(SIGINT, signalHandler);
        std::signal(SIGTERM, signalHandler);

        FocusedAppOptimizer monitor;
        monitor.run();
    }, true);
}


void FocusedAppOptimizer::stop() {
    // Try graceful shutdown first
    if (ThreadProcessUtil::killProcess("ae_fao", false)) {
        Logger(LogType::INFO) << "Stopped focused app optimizer gracefully" << std::endl;
        return;
    }
    
    // If graceful shutdown failed, try forceful termination
    if (ThreadProcessUtil::killProcess("ae_fao", true)) {
        Logger(LogType::INFO) << "Forcefully stopped focused app optimizer" << std::endl;
    } else {
        Logger(LogType::ERROR) << "Failed to stop focused app optimizer" << std::endl;
    }
}

void FocusedAppOptimizer::run() {
    Logger(LogType::INFO) << "Started focused app optimizer" << std::endl;

    while (true) {
        // Get the focused app's package name
        std::string focused_app_pkg = ShellUtil::exec(
                "dumpsys window displays | grep -E 'mCurrentFocus' | cut -d ' ' -f 5- | cut -d '}' -f 1 | cut -d '/' -f 1").second;

        if (!focused_app_pkg.empty() && AndroidUtil::isAppExists(focused_app_pkg)) {
            if (focused_app_pkg != current_focused_app) {
                // Revert the previous focused app to default state
                if (!current_focused_app.empty()) {
                    cgroupUtil.changeTaskNice(current_focused_app, 0);
                    cgroupUtil.unpinProc(current_focused_app);
                    Logger(LogType::INFO) << "Reverted previous focused app " << current_focused_app
                                          << " to original state" << std::endl;
                }

                // Optimize the new focused app
                cgroupUtil.changeTaskHighPrio(focused_app_pkg);
                cgroupUtil.pinProcOnPerf(focused_app_pkg);
                Logger(LogType::INFO) << "Optimized new focused app " << focused_app_pkg
                                      << std::endl;

                current_focused_app = focused_app_pkg;
            }
        }

        // Sleep for a short interval before checking again
        std::this_thread::sleep_for(std::chrono::seconds(2));
    }
}