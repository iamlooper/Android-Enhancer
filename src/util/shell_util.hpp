#pragma once

#include <string>

class ShellUtil {
public:
    /**
     * Execute a shell command and capture its output
     *
     * @param cmd the shell command to execute
     * @return A pair containing the exit status and the command output
     */
    static std::pair<int, std::string> exec(const std::string &cmd);
};