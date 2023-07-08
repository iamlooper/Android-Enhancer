LOCAL_PATH := $(call my-dir)/src

include $(CLEAR_VARS)
LOCAL_MODULE := android_enhancer
LOCAL_SRC_FILES := android_enhancer.cpp
include $(BUILD_EXECUTABLE)

include $(CLEAR_VARS)
LOCAL_MODULE := vmtouch
LOCAL_SRC_FILES := vmtouch.cpp
include $(BUILD_EXECUTABLE)