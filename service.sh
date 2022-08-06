#!/system/bin/sh
# AOSP Enhancer
# Author: LOOPER (iamlooper @ github)

wait_until_login() {
  # In case of /data encryption is disabled
  while [[ "$(getprop sys.boot_completed)" != "1" ]]; do
    sleep 3
  done

  # We don't have the permission to rw "/storage/emulated/0" before the user unlocks the screen
  test_file="/storage/emulated/0/Android/.PERMISSION_TEST"
  true >"$test_file"
  while [[ ! -f "$test_file" ]]; do
    true >"$test_file"
    sleep 1
  done
  rm -f "$test_file"
}

wait_until_login

# Make sure init is completed
sleep 37

# Apply enhancer
aosp_enhancer