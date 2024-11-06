LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := android_enhancer
LOCAL_SRC_FILES := src/android_enhancer.cpp src/focused_app_opt.cpp $(wildcard $(LOCAL_PATH)/src/util/*.cpp)
LOCAL_CPP_FEATURES += exceptions
include $(BUILD_EXECUTABLE)