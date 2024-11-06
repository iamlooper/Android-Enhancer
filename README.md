![Banner](https://github.com/iamlooper/Android-Enhancer/raw/main/banner.jpg)

# Android Enhancer (formerly AOSP Enhancer) 🚀

A revolutionary android optimizer. 

## Versions 🧩

- [App](https://github.com/iamlooper/Android-Enhancer-App)
- [Module](https://github.com/iamlooper/Android-Enhancer-Module)

## Download 📲

[Click here](https://www.pling.com/p/1875251/) to download the latest version of Android Enhancer. Install the app version if you want control over which tweaks to apply. Use the module version if you want a 'flash and forget' experience. Do not use the app and module versions at the same time.

## Working ⚙️

To understand the functioning of the Android Enhancer, please examine the source code from the entrypoint located [here](https://github.com/iamlooper/Android-Enhancer/blob/main/src/android_enhancer.cpp).

## Benchmarks ⚡

The given benchmarks were conducted on a Poco M3 running the GreenForce kernel on Pixel Experience Android 13.

### Scheduler latency via `hackbench` (Lower values indicate better performance)
- Without Android Enhancer: 0.919 seconds
- With Android Enhancer: 0.460 seconds

### Scheduler latency via `schbench` (Lower values indicate better performance)
- Without Android Enhancer:
`50.0th: 629
75.0th: 14480
90.0th: 32416
95.0th: 39232
*99.0th: 53696
99.5th: 60992
99.9th: 85888
min=0, max=125470`

- With Android Enhancer:
`50.0th: 351
75.0th: 13392
90.0th: 30432
95.0th: 37568
*99.0th: 53184
99.5th: 60096
99.9th: 82304
min=0, max=102169`

### Scheduler throughput via `perf bench sched messaging` (Lower values indicate better performance)
- Without Android Enhancer: 0.513 seconds
- With Android Enhancer: 0.466 seconds

### Scheduler throughput via `perf bench sched pipe` (Lower values indicate better performance)
- Without Android Enhancer: 31.806 seconds
- With Android Enhancer: 27.495 seconds

## Credits 👥

- [Chirag](https://t.me/selfmuser) - Tester
- [Leaf](https://t.me/leafinferno) - Designer
- [Jis G Jacob](https://t.me/StudioKeys) - Tester