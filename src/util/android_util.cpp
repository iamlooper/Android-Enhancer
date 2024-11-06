#include "android_util.hpp"

#include <utility>

#include "shell_util.hpp"

std::string AndroidUtil::getHomePkgName() {
    std::pair<int, std::string> result = ShellUtil::exec(
            "pm resolve-activity -a android.intent.action.MAIN -c android.intent.category.HOME | grep packageName | head -n 1 | cut -d= -f2");
    return result.second;
}

std::string AndroidUtil::getImePkgName() {
    std::pair<int, std::string> result = ShellUtil::exec(
            "ime list | grep packageName | head -n 1 | cut -d= -f2");
    return result.second;
}

bool AndroidUtil::isAppExists(const std::string &pkgName) {
    if (ShellUtil::exec("pm list packages | grep -q '^package:" + pkgName + "$'").first == 0) {
        return true;
    } else {
        return false;
    }
}