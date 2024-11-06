#pragma once

#include <iostream>
#include <cstring>
#include <unistd.h>
#include <vector>
#include <sys/resource.h>
#include <sched.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <functional>

class ThreadProcessUtil {
public:
    /**
     * Get the process ID(s) (PID) of the specified process name.
     *
     * @param processName, the name of the process.
     * @param multiple, if true, return all PIDs found, otherwise return only the first PID.
     * @return A vector of PIDs corresponding to the process name.
     */
    static std::vector<pid_t> getPid(const std::string &processName, bool multiple = false);

    /**
     * Get the thread ID(s) (TID) of the specified thread name within a process
     *
     * @param processName, the name of the process
     * @param threadName, the name of the thread
     * @param multiple, if true, return all TIDs found, otherwise return only the first TID.
     * @return A vector of TIDs corresponding to the thread name.
     */
    static std::vector<pid_t> getProcTid(const std::string &processName, const std::string &threadName,
                                         bool multiple = false);

    /**
     * Kill process with given name
     *
     * @param processName, the name of the process to kill
     * @param force, if true, use SIGKILL instead of SIGTERM
     * @param multiple, if true, kill all processes with the given name, otherwise kill only the first one
     * @return true if all targeted processes were successfully killed, false otherwise
     */
    static bool killProcess(const std::string &processName, bool force = false, bool multiple = false);

    /**
     * Kill thread with given name
     *
     * @param processName, the name of the process
     * @param threadName, the name of the thread to kill
     * @param force, if true, use SIGKILL instead of SIGTERM
     * @param multiple, if true, kill all threads with the given name, otherwise kill only the first one
     * @return true if all targeted threads were successfully killed, false otherwise
     */
    static bool killThread(const std::string &processName, const std::string &threadName, bool force = false,
                           bool multiple = false);

    /**
     * Daemonizes the current process or given function.
     */
    static void daemonize(const std::function<void()>& daemon_task = nullptr, bool close_standard_fds_only = false);
};
