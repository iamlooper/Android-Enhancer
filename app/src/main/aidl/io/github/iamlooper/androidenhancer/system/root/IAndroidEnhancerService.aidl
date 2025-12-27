package io.github.iamlooper.androidenhancer.system.root;

interface IAndroidEnhancerService {
    boolean start(String logPath, String sysfsBackupPath);
    void stop();
    boolean isRunning();
    int setMode(int modeCode);
    int getMode();
    void pushForegroundApp(String packageName);
    void setAppOverride(String packageName, int modeCode);
    void removeAppOverride(String packageName);
    void clearLog();
    String executeShellCommand(in List<String> commands);
    void setScreenState(boolean isOn);
    void setBatteryInfo(int level, int capacityMah, boolean isCharging);
    void setScreenInfo(int dpi, int widthPx, int heightPx);
    void setTouchBoostEnabled(boolean enabled);
    boolean isTouchBoostEnabled();
}


