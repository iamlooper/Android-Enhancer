#pragma once

#include <string>

// C++ version of https://github.com/yc9559/uperf/blob/master/magisk/script/libcgroup.sh
class CgroupUtil {
private:
    std::string psRet;

    /**
     * Get the number of CPU cores
     *
     * @return The number of CPU cores
     */
    int getCpuCount();

    /**
     * Generate CPU mask for all cores
     *
     * @return The CPU mask as a hex string
     */
    std::string getFullCpuMask();

    /**
     * Generate CPU mask for half of the cores
     *
     * @return The CPU mask as a hex string
     */
    std::string getHalfCpuMask();

public:
    CgroupUtil();

    /**
     * Changes the cgroup of tasks matching the given task name
     *
     * @param taskName The name of the task to match
     * @param cgroupName The name of the cgroup to move the task to
     * @param cgroupType The type of cgroup ("cpuset" or "stune")
     * @return True if the operation was successful, false otherwise
     */
    bool changeTaskCgroup(const std::string &taskName, const std::string &cgroupName, const std::string &cgroupType);

    /**
     * Changes the cgroup of processes matching the given process name
     *
     * @param processName The name of the process to match
     * @param cgroupName The name of the cgroup to move the process to
     * @param cgroupType The type of cgroup ("cpuset" or "stune")
     * @return True if the operation was successful, false otherwise
     */
    bool changeProcCgroup(const std::string &processName, const std::string &cgroupName, const std::string &cgroupType);

    /**
    * Changes the cgroup of specific threads matching the given task and thread names
    *
    * @param taskName The name of the task to match
    * @param threadName The name of the thread to match
    * @param cgroupName The name of the cgroup to move the thread to
    * @param cgroupType The type of cgroup ("cpuset" or "stune")
    * @return True if the operation was successful, false otherwise
    */
    bool changeThreadCgroup(const std::string &taskName, const std::string &threadName, const std::string &cgroupName, const std::string &cgroupType);

    /**
     * Changes the cgroup of the main thread of tasks matching the given task name
     *
     * @param taskName The name of the task to match
     * @param cgroupName The name of the cgroup to move the main thread to
     * @param cgroupType The type of cgroup ("cpuset" or "stune")
     * @return True if the operation was successful, false otherwise
     */
    bool changeMainThreadCgroup(const std::string &taskName, const std::string &cgroupName, const std::string &cgroupType);

    /**
     * Changes the CPU affinity of tasks matching the given task name
     *
     * @param taskName The name of the task to match
     * @param hexMask The CPU affinity mask in hexadecimal format
     * @return True if the operation was successful, false otherwise
     */
    bool changeTaskAffinity(const std::string &taskName, const std::string &hexMask);

    /**
     * Changes the CPU affinity of specific threads matching the given task and thread names
     *
     * @param taskName The name of the task to match
     * @param threadName The name of the thread to match
     * @param hexMask The CPU affinity mask in hexadecimal format
     * @return True if the operation was successful, false otherwise
     */
    bool changeThreadAffinity(const std::string &taskName, const std::string &threadName, const std::string &hexMask);

    /**
     * Changes the nice value of tasks matching the given task name
     *
     * @param taskName The name of the task to match
     * @param nice The nice value to set (relative to 120)
     * @return True if the operation was successful, false otherwise
     */
    bool changeTaskNice(const std::string &taskName, int nice);

    /**
     * Changes the nice value of specific threads matching the given task and thread names
     *
     * @param taskName The name of the task to match
     * @param threadName The name of the thread to match
     * @param nice The nice value to set (relative to 120)
     * @return True if the operation was successful, false otherwise
     */
    bool changeThreadNice(const std::string &taskName, const std::string &threadName, int nice);

    /**
     * Changes the real-time priority of tasks matching the given task name
     *
     * @param taskName The name of the task to match
     * @param priority The real-time priority to set (99-x, 1<=x<=99)
     * @return True if the operation was successful, false otherwise
     */
    bool changeTaskRt(const std::string &taskName, int priority);

    /**
     * Changes the real-time priority of specific threads matching the given task and thread names
     *
     * @param taskName The name of the task to match
     * @param threadName The name of the thread to match
     * @param priority The real-time priority to set (99-x, 1<=x<=99)
     * @return True if the operation was successful, false otherwise
     */
    bool changeThreadRt(const std::string &taskName, const std::string &threadName, int priority);

    /**
     * Changes the priority of tasks matching the given task name to high priority
     *
     * @param taskName The name of the task to match
     * @return True if the operation was successful, false otherwise
     */
    bool changeTaskHighPrio(const std::string &taskName);

    /**
     * Changes the priority of specific threads matching the given task and thread names to high priority
     *
     * @param taskName The name of the task to match
     * @param threadName The name of the thread to match
     * @return True if the operation was successful, false otherwise
     */
    bool changeThreadHighPrio(const std::string &taskName, const std::string &threadName);

    /**
     * Unpins a specific thread from its current CPU
     *
     * @param taskName The name of the task to match
     * @param threadName The name of the thread to unpin
     * @return True if the operation was successful, false otherwise
     */
    bool unpinThread(const std::string &taskName, const std::string &threadName);

    /**
     * Pins a specific thread to the power-efficient CPU cores
     *
     * @param taskName The name of the task to match
     * @param threadName The name of the thread to pin
     * @return True if the operation was successful, false otherwise
     */
    bool pinThreadOnPwr(const std::string &taskName, const std::string &threadName);

    /**
     * Pins a specific thread to the mid-performance CPU cores
     *
     * @param taskName The name of the task to match
     * @param threadName The name of the thread to pin
     * @return True if the operation was successful, false otherwise
     */
    bool pinThreadOnMid(const std::string &taskName, const std::string &threadName);

    /**
     * Pins a specific thread to the high-performance CPU cores
     *
     * @param taskName The name of the task to match
     * @param threadName The name of the thread to pin
     * @return True if the operation was successful, false otherwise
     */
    bool pinThreadOnPerf(const std::string &taskName, const std::string &threadName);

    /**
     * Unpins a process from its current CPU
     *
     * @param taskName The name of the task to unpin
     * @return True if the operation was successful, false otherwise
     */
    bool unpinProc(const std::string &taskName);

    /**
     * Pins a process to the power-efficient CPU cores
     *
     * @param taskName The name of the task to pin
     * @return True if the operation was successful, false otherwise
     */
    bool pinProcOnPwr(const std::string &taskName);

    /**
     * Pins a process to the mid-performance CPU cores
     *
     * @param taskName The name of the task to pin
     * @return True if the operation was successful, false otherwise
     */
    bool pinProcOnMid(const std::string &taskName);

    /**
     * Pins a process to the high-performance CPU cores
     *
     * @param taskName The name of the task to pin
     * @return True if the operation was successful, false otherwise
     */
    bool pinProcOnPerf(const std::string &taskName);

    // Rebuilds the process scan cache
    void rebuildProcessScanCache();
};