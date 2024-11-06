#pragma once

#include <iostream>
#include <cstring>
#include <unistd.h>
#include <sys/prctl.h>

#include "util/cgroup_util.hpp"

class FocusedAppOptimizer {
public:
    static void start();

    static void stop();

    static bool isRunning();

    void cleanup();

private:
    FocusedAppOptimizer();

    void run();

    CgroupUtil cgroupUtil;

    std::string current_focused_app;
};

extern FocusedAppOptimizer *_focusedAppOptimizer;

void signalHandler(int signum);