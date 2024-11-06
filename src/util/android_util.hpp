#pragma once

#include <iostream>
#include <cstring>

class AndroidUtil {
public:
    /**
     * Get the package name of the default home activity
     *
     * @return The home package name
     */
    static std::string getHomePkgName();

    /**
     * Get the package name of the current input method editor (IME)
     *
     * @return The IME package name
     */
    static std::string getImePkgName();

    /**
     * Check if an app with the given package name exists
     *
     * @param pkgName, the package name to check
     * @return true if the app exists, false otherwise
     */
    static bool isAppExists(const std::string &pkgName);
};