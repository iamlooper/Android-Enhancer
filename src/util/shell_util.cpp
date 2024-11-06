#include "shell_util.hpp"

#include <vector>
#include <sstream>
#include <cstring>

std::pair<int, std::string> ShellUtil::exec(const std::string &cmd) {
    FILE *pipe = popen(cmd.c_str(), "r");
    if (!pipe) return {-1, ""}; // Return error if pipe couldn't be opened

    std::stringstream output;
    std::vector<char> buffer(4096); // Initial buffer size

    while (fgets(buffer.data(), buffer.size(), pipe) != nullptr) {
        // If buffer is full, resize it and continue
        if (strlen(buffer.data()) == buffer.size() - 1) {
            buffer.resize(buffer.size() * 2);
            continue;
        }
        output << buffer.data();
    }

    int status = pclose(pipe);

    // Extract the result and remove the trailing newline, if any
    std::string result = output.str();
    if (!result.empty() && result.back() == '\n') result.pop_back();

    return {WEXITSTATUS(status), result};
}