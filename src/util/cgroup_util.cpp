#include "cgroup_util.hpp"

#include <vector>
#include <sstream>
#include <thread>

#include "shell_util.hpp"
#include "fs_util.hpp"
#include "logger.hpp"
#include "type_converter_util.hpp"

CgroupUtil::CgroupUtil() {
    rebuildProcessScanCache();
}

int CgroupUtil::getCpuCount() {
    return std::thread::hardware_concurrency();
}

std::string CgroupUtil::getFullCpuMask() {
    int cpu_count = getCpuCount();
    unsigned long long mask;
    if (cpu_count >= (int)(sizeof(unsigned long long) * 8)) {
        // CPU count exceeds mask size, set all bits
        mask = ~0ULL;
    } else {
        mask = (1ULL << cpu_count) - 1;
    }
    std::stringstream ss;
    ss << std::hex << mask;
    return ss.str();
}

std::string CgroupUtil::getHalfCpuMask() {
    int cpu_count = getCpuCount();
    int half_count = cpu_count / 2;
    unsigned long long mask;
    if (half_count >= (int)(sizeof(unsigned long long) * 8)) {
        mask = ~0ULL;
    } else {
        mask = (1ULL << half_count) - 1;
    }
    std::stringstream ss;
    ss << std::hex << mask;
    return ss.str();
}

bool CgroupUtil::changeTaskCgroup(const std::string &taskName, const std::string &cgroupName, const std::string &cgroupType) {
    std::pair<int, std::string> result = ShellUtil::exec("echo \"" + psRet + "\" | grep -iE \"" + taskName + "\" | awk '{print $1}'");
    std::vector<std::string> pids = TypeConverterUtil::stringToVectorString(result.second);

    for (const std::string &pid : pids) {
        std::string taskPath = "/proc/" + pid + "/task/";
        std::pair<int, std::string> tidResult = ShellUtil::exec("ls " + taskPath);
        std::vector<std::string> tids = TypeConverterUtil::stringToVectorString(tidResult.second);

        for (const std::string &tid : tids) {
            std::string cgroupPath = "/dev/" + cgroupType + "/" + cgroupName + "/tasks";
            if (!FSUtil::writeFile(cgroupPath, tid)) {
                Logger(LogType::ERROR, std::cerr) << "Failed to write to " << cgroupPath << std::endl;
                return false;
            }
        }
    }
    return true;
}

bool CgroupUtil::changeProcCgroup(const std::string &processName, const std::string &cgroupName, const std::string &cgroupType) {
    std::pair<int, std::string> result = ShellUtil::exec("echo \"" + psRet + "\" | grep -iE \"" + processName + "\" | awk '{print $1}'");
    std::vector<std::string> pids = TypeConverterUtil::stringToVectorString(result.second);

    for (const std::string &pid : pids) {
        std::string cgroupPath = "/dev/" + cgroupType + "/" + cgroupName + "/cgroup.procs";
        if (!FSUtil::writeFile(cgroupPath, pid)) {
            Logger(LogType::ERROR, std::cerr) << "Failed to write to " << cgroupPath << std::endl;
            return false;
        }
    }
    return true;
}

bool CgroupUtil::changeThreadCgroup(const std::string &taskName, const std::string &threadName, const std::string &cgroupName, const std::string &cgroupType) {
    std::pair<int, std::string> result = ShellUtil::exec("echo \"" + psRet + "\" | grep -iE \"" + taskName + "\" | awk '{print $1}'");
    std::vector<std::string> pids = TypeConverterUtil::stringToVectorString(result.second);

    for (const std::string &pid : pids) {
        std::string taskPath = "/proc/" + pid + "/task/";
        std::pair<int, std::string> tidResult = ShellUtil::exec("ls " + taskPath);
        std::vector<std::string> tids = TypeConverterUtil::stringToVectorString(tidResult.second);

        for (const std::string &tid : tids) {
            std::string commPath = taskPath + tid + "/comm";
            std::string comm = FSUtil::readFile(commPath);
            if (ShellUtil::exec("echo \"" + comm + "\" | grep -iE \"" + threadName + "\"").first == 0) {
                std::string cgroupPath = "/dev/" + cgroupType + "/" + cgroupName + "/tasks";
                if (!FSUtil::writeFile(cgroupPath, tid)) {
                    Logger(LogType::ERROR, std::cerr) << "Failed to write to " << cgroupPath << std::endl;
                    return false;
                }
            }
        }
    }
    return true;
}

bool CgroupUtil::changeMainThreadCgroup(const std::string &taskName, const std::string &cgroupName, const std::string &cgroupType) {
    std::pair<int, std::string> result = ShellUtil::exec("echo \"" + psRet + "\" | grep -iE \"" + taskName + "\" | awk '{print $1}'");
    std::vector<std::string> pids = TypeConverterUtil::stringToVectorString(result.second);

    for (const std::string &pid : pids) {
        std::string cgroupPath = "/dev/" + cgroupType + "/" + cgroupName + "/tasks";
        if (!FSUtil::writeFile(cgroupPath, pid)) {
            Logger(LogType::ERROR, std::cerr) << "Failed to write to " << cgroupPath << std::endl;
            return false;
        }
    }
    return true;
}

bool CgroupUtil::changeTaskAffinity(const std::string &taskName, const std::string &hexMask) {
    std::pair<int, std::string> result = ShellUtil::exec("echo \"" + psRet + "\" | grep -iE \"" + taskName + "\" | awk '{print $1}'");
    std::vector<std::string> pids = TypeConverterUtil::stringToVectorString(result.second);

    for (const std::string &pid : pids) {
        std::string taskPath = "/proc/" + pid + "/task/";
        std::pair<int, std::string> tidResult = ShellUtil::exec("ls " + taskPath);
        std::vector<std::string> tids = TypeConverterUtil::stringToVectorString(tidResult.second);

        for (const std::string &tid : tids) {
            std::string cmd = "taskset -p " + hexMask + " " + tid;
            int status = ShellUtil::exec(cmd).first;
            if (status == 0) {
                Logger(LogType::INFO) << "Changed CPU affinity for TID " << tid << " to " << hexMask << std::endl;
            } else {
                Logger(LogType::ERROR) << "Failed to change affinity for TID " << tid << std::endl;
                return false;
            }
        }
    }
    return true;
}

bool CgroupUtil::changeThreadAffinity(const std::string &taskName, const std::string &threadName, const std::string &hexMask) {
    std::pair<int, std::string> result = ShellUtil::exec("echo \"" + psRet + "\" | grep -iE \"" + taskName + "\" | awk '{print $1}'");
    std::vector<std::string> pids = TypeConverterUtil::stringToVectorString(result.second);

    for (const std::string &pid : pids) {
        std::string taskPath = "/proc/" + pid + "/task/";
        std::pair<int, std::string> tidResult = ShellUtil::exec("ls " + taskPath);
        std::vector<std::string> tids = TypeConverterUtil::stringToVectorString(tidResult.second);

        for (const std::string &tid : tids) {
            std::string commPath = taskPath + tid + "/comm";
            std::string comm = FSUtil::readFile(commPath);
            if (ShellUtil::exec("echo \"" + comm + "\" | grep -iE \"" + threadName + "\"").first == 0) {
                std::string cmd = "taskset -p " + hexMask + " " + tid;
                int status = ShellUtil::exec(cmd).first;
                if (status == 0) {
                    Logger(LogType::INFO) << "Changed CPU affinity for thread " << threadName << " (TID " << tid << ") to " << hexMask << std::endl;
                } else {
                    Logger(LogType::ERROR) << "Failed to change affinity for TID " << tid << std::endl;
                    return false;
                }
            }
        }
    }
    return true;
}

bool CgroupUtil::changeTaskNice(const std::string &taskName, int nice) {
    std::pair<int, std::string> result = ShellUtil::exec("echo \"" + psRet + "\" | grep -iE \"" + taskName + "\" | awk '{print $1}'");
    std::vector<std::string> pids = TypeConverterUtil::stringToVectorString(result.second);

    for (const std::string &pid : pids) {
        std::string taskPath = "/proc/" + pid + "/task/";
        std::pair<int, std::string> tidResult = ShellUtil::exec("ls " + taskPath);
        std::vector<std::string> tids = TypeConverterUtil::stringToVectorString(tidResult.second);

        for (const std::string &tid : tids) {
            std::string cmd = "renice -n " + std::to_string(nice) + " -p " + tid;
            int status = ShellUtil::exec(cmd).first;
            if (status == 0) {
                Logger(LogType::INFO) << "Changed nice value for TID " << tid << " to " << nice << std::endl;
            } else {
                Logger(LogType::ERROR) << "Failed to change nice value for TID " << tid << std::endl;
                return false;
            }
        }
    }
    return true;
}

bool CgroupUtil::changeThreadNice(const std::string &taskName, const std::string &threadName, int nice) {
    std::pair<int, std::string> result = ShellUtil::exec("echo \"" + psRet + "\" | grep -iE \"" + taskName + "\" | awk '{print $1}'");
    std::vector<std::string> pids = TypeConverterUtil::stringToVectorString(result.second);

    for (const std::string &pid : pids) {
        std::string taskPath = "/proc/" + pid + "/task/";
        std::pair<int, std::string> tidResult = ShellUtil::exec("ls " + taskPath);
        std::vector<std::string> tids = TypeConverterUtil::stringToVectorString(tidResult.second);

        for (const std::string &tid : tids) {
            std::string commPath = taskPath + tid + "/comm";
            std::string comm = FSUtil::readFile(commPath);
            if (ShellUtil::exec("echo \"" + comm + "\" | grep -iE \"" + threadName + "\"").first == 0) {
                std::string cmd = "renice -n " + std::to_string(nice) + " -p " + tid;
                int status = ShellUtil::exec(cmd).first;
                if (status == 0) {
                    Logger(LogType::INFO) << "Changed nice value for thread " << threadName << " (TID " << tid << ") to " << nice << std::endl;
                } else {
                    Logger(LogType::ERROR) << "Failed to change nice value for TID " << tid << std::endl;
                    return false;
                }
            }
        }
    }
    return true;
}

bool CgroupUtil::changeTaskRt(const std::string &taskName, int priority) {
    std::pair<int, std::string> result = ShellUtil::exec("echo \"" + psRet + "\" | grep -iE \"" + taskName + "\" | awk '{print $1}'");
    std::vector<std::string> pids = TypeConverterUtil::stringToVectorString(result.second);

    for (const std::string &pid : pids) {
        std::string taskPath = "/proc/" + pid + "/task/";
        std::pair<int, std::string> tidResult = ShellUtil::exec("ls " + taskPath);
        std::vector<std::string> tids = TypeConverterUtil::stringToVectorString(tidResult.second);

        for (const std::string &tid : tids) {
            std::string cmd = "chrt -f -p " + std::to_string(priority) + " " + tid;
            int status = ShellUtil::exec(cmd).first;
            if (status == 0) {
                Logger(LogType::INFO) << "Changed real-time priority for TID " << tid << " to " << priority << std::endl;
            } else {
                Logger(LogType::ERROR) << "Failed to change real-time priority for TID " << tid << std::endl;
                return false;
            }
        }
    }
    return true;
}

bool CgroupUtil::changeThreadRt(const std::string &taskName, const std::string &threadName, int priority) {
    std::pair<int, std::string> result = ShellUtil::exec("echo \"" + psRet + "\" | grep -iE \"" + taskName + "\" | awk '{print $1}'");
    std::vector<std::string> pids = TypeConverterUtil::stringToVectorString(result.second);

    for (const std::string &pid : pids) {
        std::string taskPath = "/proc/" + pid + "/task/";
        std::pair<int, std::string> tidResult = ShellUtil::exec("ls " + taskPath);
        std::vector<std::string> tids = TypeConverterUtil::stringToVectorString(tidResult.second);

        for (const std::string &tid : tids) {
            std::string commPath = taskPath + tid + "/comm";
            std::string comm = FSUtil::readFile(commPath);
            if (ShellUtil::exec("echo \"" + comm + "\" | grep -iE \"" + threadName + "\"").first == 0) {
                std::string cmd = "chrt -f -p " + std::to_string(priority) + " " + tid;
                int status = ShellUtil::exec(cmd).first;
                if (status == 0) {
                    Logger(LogType::INFO) << "Changed real-time priority for thread " << threadName << " (TID " << tid << ") to " << priority << std::endl;
                } else {
                    Logger(LogType::ERROR) << "Failed to change real-time priority for TID " << tid << std::endl;
                    return false;
                }
            }
        }
    }
    return true;
}

bool CgroupUtil::changeTaskHighPrio(const std::string &taskName) {
    return changeTaskNice(taskName, -15);
}

bool CgroupUtil::changeThreadHighPrio(const std::string &taskName, const std::string &threadName) {
    return changeThreadNice(taskName, threadName, -15);
}

bool CgroupUtil::unpinThread(const std::string &taskName, const std::string &threadName) {
    return changeThreadCgroup(taskName, threadName, "", "cpuset");
}

bool CgroupUtil::pinThreadOnPwr(const std::string &taskName, const std::string &threadName) {
    return changeThreadCgroup(taskName, threadName, "background", "cpuset");
}

bool CgroupUtil::pinThreadOnMid(const std::string &taskName, const std::string &threadName) {
    if (!unpinThread(taskName, threadName)) {
        return false;
    }
    std::string halfMask = getHalfCpuMask();
    return changeThreadAffinity(taskName, threadName, halfMask);
}

bool CgroupUtil::pinThreadOnPerf(const std::string &taskName, const std::string &threadName) {
    if (!unpinThread(taskName, threadName)) {
        return false;
    }
    std::string fullMask = getFullCpuMask();
    return changeThreadAffinity(taskName, threadName, fullMask);
}

bool CgroupUtil::unpinProc(const std::string &taskName) {
    return changeTaskCgroup(taskName, "", "cpuset");
}

bool CgroupUtil::pinProcOnPwr(const std::string &taskName) {
    return changeTaskCgroup(taskName, "background", "cpuset");
}

bool CgroupUtil::pinProcOnMid(const std::string &taskName) {
    if (!unpinProc(taskName)) {
        return false;
    }
    std::string halfMask = getHalfCpuMask();
    return changeTaskAffinity(taskName, halfMask);
}

bool CgroupUtil::pinProcOnPerf(const std::string &taskName) {
    if (!unpinProc(taskName)) {
        return false;
    }
    std::string fullMask = getFullCpuMask();
    return changeTaskAffinity(taskName, fullMask);
}

void CgroupUtil::rebuildProcessScanCache() {
    std::pair<int, std::string> result = ShellUtil::exec("ps -Ao pid,args");
    if (result.first == 0) {
        psRet = result.second;
    } else {
        Logger(LogType::ERROR, std::cerr) << "Failed to rebuild process scan cache" << std::endl;
    }
}