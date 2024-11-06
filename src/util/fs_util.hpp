#pragma once

#include <iostream>
#include <climits>
#include <cstring>
#include <vector>
#include <sys/stat.h>

#include <unistd.h>


class FSUtil {
public:
    /**
     * Reads the content of a file and returns it as a string
     *
     * @param filename, the path to the file
     * @return The content of the file as a string
     */
    static std::string readFile(const std::string &filename);

    /**
     * Writes the given content to a file
     *
     * @param fileName, the name of the file to mutate
     * @param content, the content to be written to the file
     * @param append, whether the content should be appended to the file
     * @return true if the write operation is successful, false otherwise
     */
    static bool writeFile(const std::string &fileName, const std::string &content, bool append = false);

    /**
     * Creates a folder at the specified path
     *
     * @param folderPath, the path where the folder should be created
     * @return true if the folder is created successfully, false otherwise
     */
    static bool createFolder(const std::string &folderPath);

    /**
     * Deletes a folder at the specified path
     *
     * @param folderPath, the path to the folder to be deleted
     * @return true if the folder is deleted successfully, false otherwise
     */
    static bool deleteFolder(const std::string &folderPath);

    /**
     * Checks if the given path exists
     *
     * @param path, the path to a file or folder
     * @return true if the path exists, false otherwise
     */
    static bool isPathExists(const std::string &path);

    /**
     * Get a list of paths from a wildcard path
     *
     * @param wildcardPath, the path containing a wildcard (*)
     * @return A vector of expanded paths
     */
    static std::vector <std::string> getPathsFromWp(const std::string &wildcardPath);

    /**
     * Remove a path with support for wildcard path deletion
     *
     * @param path, the path or wildcard path to be removed
     */
    static void removePath(const std::string &path);

    /**
     * Get the full path of the current executable
     *
     * @return The full path of the executable or an empty string on failure
     */
    static std::string getExecutablePath() {
        char result[PATH_MAX];
        ssize_t count = readlink("/proc/self/exe", result, PATH_MAX);
        if (count != -1) {
            result[count] = '\0';
            return std::string(result);
        }
        return "";
    }

    /**
     * Extract the directory path from the full executable path
     *
     * @return The directory containing the executable or an empty string on failure
     */
    static std::string getExecutableDirectory() {
        std::string path = getExecutablePath();
        size_t found = path.find_last_of("/\\");
        return (found != std::string::npos) ? path.substr(0, found) : "";
    }
};