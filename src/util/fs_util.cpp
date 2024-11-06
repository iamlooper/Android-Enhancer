#include "fs_util.hpp"

#include <iostream>
#include <utility>
#include <fstream>
#include <sstream>

#include "logger.hpp"
#include "shell_util.hpp"

std::string FSUtil::readFile(const std::string &filename) {
    std::ifstream file(filename);
    std::string content;
    if (file.is_open()) {
        std::string line;
        while (std::getline(file, line)) {
            content += line + "\n";
        }
        file.close();
    } else {
        Logger(LogType::ERROR, std::cerr) << "Unable to open file " << filename << std::endl;
    }
    return content;
}

bool FSUtil::writeFile(const std::string &fileName, const std::string &content, bool append) {
    // Check if the file exists
    if (!isPathExists(fileName)) {
        // File does not exist, return false
        Logger(LogType::ERROR) << fileName << " does not exist" << std::endl;
        return false;
    }

    // Determine the open mode based on the append flag
    std::ios_base::openmode mode = std::ios::out;
    if (append) {
        mode |= std::ios::app;
    }

    // Write the content
    std::ofstream outFile(fileName.c_str(), mode);
    if (!outFile.is_open()) {
        // Failed to open the file for writing, return false
        Logger(LogType::ERROR, std::cerr) << "Failed to open " << fileName << " for writing" << std::endl;
        return false;
    }
    outFile << content;
    outFile.close();

    // Log the success
    Logger(LogType::INFO) << fileName << " -> " << content << std::endl;
    return true;
}

bool FSUtil::createFolder(const std::string &folderPath) {
    if (mkdir(folderPath.c_str(), 0777) == 0) {
        return true;
    } else {
        Logger(LogType::ERROR, std::cerr) << "Unable to create folder " << folderPath << std::endl;
        return false;
    }
}

bool FSUtil::deleteFolder(const std::string &folderPath) {
    if (rmdir(folderPath.c_str()) == 0) {
        return true;
    } else {
        Logger(LogType::ERROR, std::cerr) << "Unable to delete folder " << folderPath << std::endl;
        return false;
    }
}

bool FSUtil::isPathExists(const std::string &path) {
    struct stat info;
    int ret = stat(path.c_str(), &info);
    return ret == 0;
}

std::vector <std::string> FSUtil::getPathsFromWp(const std::string &wildcardPath) {
    // Execute a shell command to retrieve the output of the wildcard expansion
    std::pair<int, std::string> result = ShellUtil::exec(
            "for i in " + wildcardPath + "; do echo $i; done");

    // Split the output by line and store it in a vector
    std::vector <std::string> paths;
    std::stringstream stream(result.second);
    std::string path;
    while (getline(stream, path)) {
        paths.push_back(path);
    }
    return paths;
}

void FSUtil::removePath(const std::string &path) {
    // If the path contains an asterisk, then use the getPathsFromWp() function to get a list of paths and remove them
    if (path.find('*') != std::string::npos) {
        std::vector <std::string> paths = getPathsFromWp(path);
        for (const std::string &p: paths) {
            remove(p.c_str());
        }
    } else {
        // If path does not contain an asterisk, then continue with normal method
        remove(path.c_str());
    }
}