#include "thread_process_util.hpp"

#include <csignal>
#include <cstdlib>
#include <fstream>

#include "logger.hpp"
#include "shell_util.hpp"
#include "type_converter_util.hpp"

std::vector <pid_t>
ThreadProcessUtil::getPid(const std::string &processName, bool multiple) {
    std::vector <pid_t> pids;
    std::string command = "ps -Ao pid,args,cmd | grep -iE '" + processName + "' | grep -v grep | awk '{print $1}'";
    if (!multiple) {
        command += " | head -n 1";
    }
    std::pair<int, std::string> result = ShellUtil::exec(command);
    std::vector <std::string> stringPids = TypeConverterUtil::stringToVectorString(result.second);

    for (const std::string &stringPid: stringPids) {
        if (!stringPid.empty()) {
            pids.push_back(TypeConverterUtil::to<pid_t>(stringPid));
        }
    }

    return pids;
}

std::vector <pid_t>
ThreadProcessUtil::getProcTid(const std::string &processName, const std::string &threadName,
                               bool multiple) {
    std::vector <pid_t> tids;
    pid_t pid = getPid(processName)[0];
    std::pair<int, std::string> result = ShellUtil::exec(
            "ls /proc/" + std::to_string(pid) + "/task/");
    std::vector <std::string> stringTids = TypeConverterUtil::stringToVectorString(result.second);

    for (const std::string &stringTid: stringTids) {
        if (!stringTid.empty()) {
            std::string tidPath = "/proc/" + std::to_string(pid) + "/task/" + stringTid + "/comm";
            std::ifstream tidFile(tidPath);
            std::string currentThreadName;
            tidFile >> currentThreadName;

            if (currentThreadName.find(threadName) != std::string::npos) {
                tids.push_back(TypeConverterUtil::to<pid_t>(stringTid));
                if (!multiple) break;
            }
        }
    }

    return tids;
}

bool
ThreadProcessUtil::killProcess(const std::string &processName, bool force, bool multiple) {
    std::vector <pid_t> pids = getPid(processName, multiple);
    std::string killCommand = force ? "kill -9 " : "kill ";

    for (const pid_t &pid: pids) {
        std::string command = killCommand + std::to_string(pid);
        std::pair<int, std::string> result = ShellUtil::exec(command);
        if (result.first == 0) {
            Logger(LogType::INFO) << "Successfully killed process " << processName << " (PID: " << pid << ")" << std::endl;
        } else {
            Logger(LogType::ERROR) << "Failed to kill process " << processName << " (PID: " << pid << ")" << std::endl;
            return false;
        }
    }

    return true;
}

bool
ThreadProcessUtil::killThread(const std::string &processName, const std::string &threadName,
                               bool force, bool multiple) {
    std::vector <pid_t> tids = getProcTid(processName, threadName, multiple);
    std::string killCommand = force ? "kill -9 " : "kill ";

    for (const pid_t &tid: tids) {
        std::string command = killCommand + std::to_string(tid);
        std::pair<int, std::string> result = ShellUtil::exec(command);
        if (result.first == 0) {
            Logger(LogType::INFO) << "Successfully killed thread " << threadName << " (TID: " << tid << ")" << std::endl;
        } else {
            Logger(LogType::ERROR) << "Failed to kill thread " << threadName << " (TID: " << tid << ")" << std::endl;
            return false;
        }
    }

    return true;
}

void ThreadProcessUtil::daemonize(const std::function<void()>& daemon_task, bool close_standard_fds_only) {
    pid_t pid;

    if (daemon_task == nullptr) {
        // No function provided; daemonize the entire process

        // Fork off the parent process
        pid = fork();
        if (pid < 0) {
            exit(EXIT_FAILURE);
        }

        // Let the parent terminate
        if (pid > 0) {
            exit(EXIT_SUCCESS);
        }

        // On success: The child process becomes session leader
        if (setsid() < 0) {
            exit(EXIT_FAILURE);
        }

        // Ignore signals
        std::signal(SIGCHLD, SIG_IGN);
        std::signal(SIGHUP, SIG_IGN);

        // Fork off for the second time
        pid = fork();
        if (pid < 0) {
            exit(EXIT_FAILURE);
        }

        // Let the parent terminate
        if (pid > 0) {
            exit(EXIT_SUCCESS);
        }

        // Set new file permissions
        umask(0);

        // Change the working directory to the root directory
        if (chdir("/") < 0) {
            exit(EXIT_FAILURE);
        }

        // Close file descriptors
        if (close_standard_fds_only) {
            // Close only stdin, stdout, stderr
            close(STDIN_FILENO);
            close(STDOUT_FILENO);
            close(STDERR_FILENO);
        } else {
            // Close all open file descriptors
            for (int fd = sysconf(_SC_OPEN_MAX); fd >= 0; fd--) {
                close(fd);
            }
        }

        // Reopen stdin, stdout, stderr to /dev/null
        int fd0 = open("/dev/null", O_RDWR);
        if (fd0 != -1) {
            dup2(fd0, STDIN_FILENO);
            dup2(fd0, STDOUT_FILENO);
            dup2(fd0, STDERR_FILENO);

            // Close the extra file descriptor if it's not standard input/output/error
            if (fd0 > STDERR_FILENO) {
                close(fd0);
            }
        } else {
            exit(EXIT_FAILURE);
        }

        // Daemon code continues here
    } else {
        // Function provided; daemonize only the specified task

        // Fork off the parent process
        pid = fork();
        if (pid < 0) {
            exit(EXIT_FAILURE);
        }

        // Let the parent continue
        if (pid > 0) {
            return; // Parent continues execution
        }

        // Child process becomes session leader
        if (setsid() < 0) {
            exit(EXIT_FAILURE);
        }

        // Ignore signals
        std::signal(SIGCHLD, SIG_IGN);
        std::signal(SIGHUP, SIG_IGN);

        // Fork off for the second time
        pid = fork();
        if (pid < 0) {
            exit(EXIT_FAILURE);
        }

        // Let the first child exit
        if (pid > 0) {
            exit(EXIT_SUCCESS);
        }

        // Set new file permissions
        umask(0);

        // Change the working directory to the root directory
        if (chdir("/") < 0) {
            exit(EXIT_FAILURE);
        }

        // Close file descriptors
        if (close_standard_fds_only) {
            // Close only stdin, stdout, stderr
            close(STDIN_FILENO);
            close(STDOUT_FILENO);
            close(STDERR_FILENO);
        } else {
            // Close all open file descriptors
            for (int fd = sysconf(_SC_OPEN_MAX); fd >= 0; fd--) {
                close(fd);
            }
        }

        // Reopen stdin, stdout, stderr to /dev/null
        int fd0 = open("/dev/null", O_RDWR);
        if (fd0 != -1) {
            dup2(fd0, STDIN_FILENO);
            dup2(fd0, STDOUT_FILENO);
            dup2(fd0, STDERR_FILENO);

            // Close the extra file descriptor if it's not standard input/output/error
            if (fd0 > STDERR_FILENO) {
                close(fd0);
            }
        } else {
            exit(EXIT_FAILURE);
        }

        // Execute the daemon task
        daemon_task();

        exit(EXIT_SUCCESS);
    }
}