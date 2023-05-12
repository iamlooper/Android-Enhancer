![Android-Enhancer](https://github.com/iamlooper/Android-Enhancer/raw/main/android_enhancer.jpeg)

# Android Enhancer (formerly AOSP Enhancer) 🚀
A revolutionary android optimizer. 

## Variants 🧩
- [Android App](https://github.com/iamlooper/Android-Enhancer/blob/main/app)
- [Magisk Module](https://github.com/iamlooper/Android-Enhancer/blob/main/magisk)

## Download 📲
You can download Android Enhancer from the following:
- [Pling](https://www.pling.com/p/1875251)
- [Buy me a coffee](https://buymeacoffee.com/iamlooper/posts) (Early access)

## Description 📝
Android Enhancer is a specialized tool that helps to optimize the performance of android devices by modifying certain core parameters. Unlike other optimizers, Android Enhancer uses a simple and universal approach that allows it to work on a wide range of android devices. This means that it can be used to improve the performance of a wide variety of devices, including smartphones, tablets, and other android-powered devices.

One of the key benefits of Android Enhancer is that it is designed to provide better and more sustainable performance, while also helping to improve battery life. This is achieved by carefully adjusting a small number of kernel parameters, which are the underlying settings that control how the android operating system functions. By modifying these parameters in a specific way, Android Enhancer is able to improve the overall performance and efficiency of the device.

Overall, Android Enhancer is an effective tool for optimizing the performance of android devices, and is particularly useful for those who want to improve their device's performance without making any major changes or modifications.

## Explanation 🔍
Well, the underlying logic is all about types of tweaks shown on the app. Android Enhancer tweaks are generally compatible with every android-powered device, though it is not guaranteed that they will work on every device. First of all, Android Enhancer tweaks various logging-related kernel parameters under `/proc/sys/kernel`. By disabling these parameters, it is ensured that unnecessary logging is avoided, henceforth overheads in the device's performance decrease. After this, Android Enhancer tweaks core android processes such as `zygote`, `surfaceflinger`, and `system_server`. This is done by specifically adjusting the priority of these processes. As a result of this operation, the device's performance is increased as core processes get more resources to work better on.

If we go on to MIUI-specific tweaks, then in this case, various unnecessary MIUI apps like `com.miui.daemon` & `com.xiaomi.joyose` are disabled. This relieves the device from resource consumption and boosts battery juice. It is not recommended by me to fully disable a system app as it can result in abnormal behaviour of the system. So, it is preferred to disable the services/processes inside a system app rather than fully disabling a system app. 

System properties are also tweaked to ensure a more optimal user experience, which includes disabling of logging props and enabling of zygote preforking (creates an empty copy of zygote, which enables the app to launch faster as the time for creating zygote copy to run the app is completely eliminated by already creating it). The next thing that the app does is tweaking dalvik. This is done by using system commands to optimize dalvik, such as `cmd package compile`, which is used to compile dalvik data of a specific app with predefined profiles in the system. Tweaking dalvik generally improves app opening speed and responsiveness, but it can also slow down the installation speed of an app as the dalvik data of the app is been optimized at that time. Lastly, various logging processes are killed, such as `statsd` and `traced`.

## Instructions 📜
For app:
- Install app & execute preferred tweaks.
- Wait for a few seconds to complete execution.
- Enjoy enhanced experience.

For magisk module:
- Flash & reboot.
- Check for logs in Internal Storage / Android.
- Forget that you installed it in first place if you liked it & you are seeing a improvement. 

## Notes 🗒️
- Every device is not compatible so kindly note this before giving feedback.
- Don't expect any black magic to occur so that your device suddenly transforms into ROG Phone 6.

## App Screenshots 📱
[<img src="https://github.com/iamlooper/Android-Enhancer/raw/main/app/screenshots/ss_01.png" width=140>](https://github.com/iamlooper/Android-Enhancer/raw/main/app/screenshots/ss_01.png)
[<img src="https://github.com/iamlooper/Android-Enhancer/raw/main/app/screenshots/ss_02.png" width=140>](https://github.com/iamlooper/Android-Enhancer/raw/main/app/screenshots/ss_02.png)
[<img src="https://github.com/iamlooper/Android-Enhancer/raw/main/app/screenshots/ss_03.png" width=140>](https://github.com/iamlooper/Android-Enhancer/raw/main/app/screenshots/ss_03.png)
[<img src="https://github.com/iamlooper/Android-Enhancer/raw/main/app/screenshots/ss_04.png" width=140>](https://github.com/iamlooper/Android-Enhancer/raw/main/app/screenshots/ss_04.png)
[<img src="https://github.com/iamlooper/Android-Enhancer/raw/main/app/screenshots/ss_05.png" width=140>](https://github.com/iamlooper/Android-Enhancer/raw/main/app/screenshots/ss_05.png)

## Support 💙
If you liked any one of my projects then consider supporting me via following:
- [Buy me a coffee](https://buymeacoffee.com/iamlooper/membership)
- [Join telegram channel](https://loopprojects.t.me)