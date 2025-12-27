# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# AIDL Interface for RootService IPC
# Accessed via reflection through Binder.Stub.asInterface()
-keep class io.github.iamlooper.androidenhancer.system.root.IAndroidEnhancerService { *; }
-keep class io.github.iamlooper.androidenhancer.system.root.IAndroidEnhancerService$Stub { *; }
-keep class io.github.iamlooper.androidenhancer.system.root.IAndroidEnhancerService$Stub$Proxy { *; }

# JNI Bridge - native methods called from native code by name
-keep class io.github.iamlooper.androidenhancer.system.jni.JniBridge { *; }

# AndroidEnhancerMode - enum values used by JNI/native code
-keep enum io.github.iamlooper.androidenhancer.system.jni.AndroidEnhancerMode { *; }

# Kotlin Serialization - @Serializable data class
-keepclassmembers class io.github.iamlooper.androidenhancer.data.local.PreferencesSnapshot { *; }

# Preserve source file names and line numbers for crash reports
-keepattributes SourceFile,LineNumberTable