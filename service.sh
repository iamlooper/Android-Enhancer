#!/system/bin/sh
# Android Enhancer
# Author: Looper (iamlooper @ github)

MODDIR="${0%/*}"

wait_until_login() {
  # In case of /data encryption is disabled.
  while [[ "$(getprop sys.boot_completed)" != "1" ]]; do
    sleep 3
  done

  # We don't have the permission to rw "/storage/emulated/0" before the user unlocks the screen.
  test_file="/storage/emulated/0/Android/.PERMISSION_TEST"
  true >"$test_file"
  while [[ ! -f "$test_file" ]]; do
    true >"$test_file"
    sleep 3
  done
  rm -f "$test_file"
}

wait_until_login

# Make sure init is completed.
sleep 30

# Move `vmtouch` binary in /data/local/tmp.
cp -f "$MODDIR/libs/libvmtouch.so" '/data/local/tmp/vmtouch'
chmod 0777 '/data/local/tmp/vmtouch'

# Execute Android Enhancer.
"$MODDIR/libs/libandroid_enhancer.so" --all-tweaks 1>'/storage/emulated/0/Android/android_enhancer.txt' 2>'/data/local/tmp/android_enhancer_debug.txt' && cp -f '/data/local/tmp/android_enhancer_debug.txt' '/storage/emulated/0/Android' &