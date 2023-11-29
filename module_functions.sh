#!/sbin/sh

############################################
# Module Functions
############################################

###################
# Helper Functions
###################

ui_print() { echo "$1"; }

toupper() {
  echo "$@" | tr '[:lower:]' '[:upper:]'
}

grep_cmdline() {
  local REGEX="s/^$1=//p"
  { echo $(cat /proc/cmdline)$(sed -e 's/[^"]//g' -e 's/""//g' /proc/cmdline) | xargs -n 1; \
    sed -e 's/ = /=/g' -e 's/, /,/g' -e 's/"//g' /proc/bootconfig; \
  } 2>/dev/null | sed -n "$REGEX"
}

grep_prop() {
  local REGEX="s/^$1=//p"
  shift
  local FILES="$@"
  [[ -z "$FILES" ]] && FILES=/system/build.prop
  cat "$FILES" 2>/dev/null | dos2unix | sed -n "$REGEX" | head -n 1
}

grep_get_prop() {
  local result="$(grep_prop $@)"
  [[ -z "$result" ]] && {
    # Fallback to getprop
    getprop "$1"
  } || {
    echo "$result"
  }
}

is_mounted() {
  grep -q " $(readlink -f $1) " /proc/mounts 2>/dev/null
  return "$?"
}

set_nvbase() {
  NVBASE="$1"
  "$KSU" && {
    BIN="$1/ksu/bin"
  } || {
    BIN="$1/magisk"
  }
}

abort() {
  ui_print "$1"
  [[ ! -z "$MODPATH" ]] && rm -rf "$MODPATH"
  rm -rf "$TMPDIR"
  exit 1
}

print_title() {
  local len line1len line2len bar
  line1len="$(echo -n $1 | wc -c)"
  line2len="$(echo -n $2 | wc -c)"
  len="$line2len"
  [[ "$line1len" -gt "$line2len" ]] && len="$line1len"
  len="$((len + 2))"
  bar="$(printf "%${len}s" | tr ' ' '*')"
  ui_print "$bar"
  ui_print " $1 "
  [[ "$2" ]] && ui_print " $2 "
  ui_print "$bar"
}

######################
# Environment Related
######################

setup_flashable() {
  ensure_bb
}

ensure_bb() {
  set -o | grep -q standalone && {
    # Already in BusyBox environment.
    set -o standalone
    return
  }

  # Find the busybox binary.
  local bb
  [[ -f "$BIN/busybox" ]] && {
    bb="$BIN/busybox"
  } || {
    abort "[Error] Cannot find BusyBox"
  }
  chmod 0755 "$bb"

  # Busybox could be a script, make sure /system/bin/sh exists.
  [[ ! -f /system/bin/sh ]] && {
    umount -l /system 2>/dev/null
    mkdir -p /system/bin
    ln -s "$(command -v sh)" /system/bin/sh
  }

  export ASH_STANDALONE=1

  # Find our current arguments.
  # Run in busybox environment to ensure consistent results.
  # /proc/<pid>/cmdline shall be <interpreter> <script> <arguments...>
  local cmds="$($bb sh -c "
  for arg in \$(tr '\0' '\n' < /proc/$$/cmdline); do
    [[ -z \"\$cmds\" ]] && {
      # Skip the first argument as we want to change the interpreter.
      cmds=\"sh\"
    } || {
      cmds=\"\$cmds '\$arg'\"
    }
  done
  echo \$cmds")"

  # Re-exec our script in BusyBox environment.
  echo "$cmds" | "$bb" xargs "$bb"
  exit
}

#######################
# Installation Related
#######################

# setup_mntpoint <mountpoint>
setup_mntpoint() {
  local POINT="$1"
  [[ -L "$POINT" ]] && mv -f "$POINT" ${POINT}_link
  [[ ! -d "$POINT" ]] && {
    rm -f "$POINT"
    mkdir -p "$POINT"
  }
}

# After calling this method, the following variables will be set:
# SLOT, SYSTEM_AS_ROOT, LEGACYSAR
mount_partitions() {
  # Check A/B slot.
  SLOT="$(grep_cmdline androidboot.slot_suffix)"
  [[ -z "$SLOT" ]] && {
    SLOT="$(grep_cmdline androidboot.slot)"
    [[ -z "$SLOT" ]] || SLOT=_${SLOT}
  }
  [[ "$SLOT" == "normal" ]] && unset SLOT
  [[ -z "$SLOT" ]] || ui_print "[Info] Current boot slot: $SLOT"

  # Mount ro partitions.
  [[ "$(is_mounted /system_root)" ]] && {
    umount /system 2>/dev/null
    umount /system_root 2>/dev/null
  }
  [[ -f /system/init ]] && [[ -L /system/init ]] && {
    SYSTEM_AS_ROOT=true
    setup_mntpoint /system_root
    [[ ! "$(mount --move /system /system_root)" ]] && {
      umount /system
      umount -l /system 2>/dev/null
    }
    mount -o bind /system_root/system /system
  } || {
    grep ' / ' /proc/mounts | grep -qv 'rootfs' || grep -q ' /system_root ' /proc/mounts && {
      SYSTEM_AS_ROOT=true
    } || {
      SYSTEM_AS_ROOT=false
    }
  }
  "$SYSTEM_AS_ROOT" && ui_print "[Info] Device is system-as-root"

  LEGACYSAR=false
  grep ' / ' /proc/mounts | grep -q '/dev/root' && LEGACYSAR=true
}

api_level_arch_detect() {
  API="$(grep_get_prop ro.build.version.sdk)"
  ABI="$(grep_get_prop ro.product.cpu.abi)"
  if [[ "$ABI" == "x86" ]]; then
    ARCH=x86
    ABI32=x86
    IS64BIT=false
  elif [[ "$ABI" == "arm64-v8a" ]]; then
    ARCH=arm64
    ABI32=armeabi-v7a
    IS64BIT=true
  elif [[ "$ABI" == "x86_64" ]]; then
    ARCH=x64
    ABI32=x86
    IS64BIT=true
  else
    ARCH=arm
    ABI=armeabi-v7a
    ABI32=armeabi-v7a
    IS64BIT=false
  fi
}

copy_preinit_files() {
  local PREINITDIR="$(magisk --path)/.magisk/preinit"
  [[ ! "$(grep -q " $PREINITDIR " /proc/mounts)" ]] && {
    ui_print "[Error] Unable to find preinit dir"
    return 1
  }

  [[ ! "$(grep -q "/adb/modules $PREINITDIR " /proc/self/mountinfo)" ]] && rm -rf "$PREINITDIR"/*

  # Copy all enabled sepolicy.rule
  for r in "$NVBASE"/modules*/*/sepolicy.rule; do
    [[ -f "$r" ]] || continue
    local MODDIR="${r%/*}"
    [[ -f "$MODDIR/disable" ]] && continue
    [[ -f "$MODDIR/remove" ]] && continue
    [[ -f "$MODDIR/update" ]] && continue
    local MODNAME="${MODDIR##*/}"
    mkdir -p "$PREINITDIR/$MODNAME"
    cp -f "$r" "$PREINITDIR/$MODNAME/sepolicy.rule"
  done
}

# Use `api_level_arch_detect` function to setup native libraries.
configure_native_libs() {
  if [[ "$ARCH" == "arm64" ]]; then
    mv -f "$MODPATH"/libs/arm64-v8a/* "$MODPATH/libs"
    rm -rf "$MODPATH/libs/arm64-v8a"
    rm -rf "$MODPATH/libs/armeabi-v7a"
    rm -rf "$MODPATH/libs/x86"
    rm -rf "$MODPATH/libs/x86_64"
  elif [[ "$ARCH" == "arm" ]]; then
    mv -f "$MODPATH"/libs/armeabi-v7a/* "$MODPATH/libs"
    rm -rf "$MODPATH/libs/armeabi-v7a"
    rm -rf "$MODPATH/libs/arm64-v8a"
    rm -rf "$MODPATH/libs/x86"
    rm -rf "$MODPATH/libs/x86_64"
  elif [[ "$ARCH" == "x86" ]]; then
    mv -f "$MODPATH"/libs/x86/* "$MODPATH/libs"
    rm -rf "$MODPATH/libs/x86"
    rm -rf "$MODPATH/libs/armeabi-v7a"
    rm -rf "$MODPATH/libs/arm64-v8a"
    rm -rf "$MODPATH/libs/x86_64"
  elif [[ "$ARCH" == "x64" ]]; then
    mv -f "$MODPATH"/libs/x86_64/* "$MODPATH/libs"
    rm -rf "$MODPATH/libs/x86_64"
    rm -rf "$MODPATH/libs/armeabi-v7a"
    rm -rf "$MODPATH/libs/x86"
    rm -rf "$MODPATH/libs/arm64-v8a"
  fi
}

#################
# Module Related
#################

set_perm() {
  chown "$2:$3" "$1" || return 1
  chmod "$4" "$1" || return 1
  local CON="$5"
  [[ -z "$CON" ]] && CON=u:object_r:system_file:s0
  chcon "$CON" "$1" || return 1
}

set_perm_recursive() {
  find "$1" -type d 2>/dev/null | while read dir; do
    set_perm "$dir" "$2" "$3" "$4" "$6"
  done
  find "$1" -type f -o -type l 2>/dev/null | while read file; do
    set_perm "$file" "$2" "$3" "$5" "$6"
  done
}

mktouch() {
  mkdir -p "${1%/*}" 2>/dev/null
  [[ -z "$2" ]] && touch "$1" || echo "$2" > "$1"
  chmod 0644 "$1"
}

load_vksel() { source "$MODPATH/addon/VKS/install.sh"; }

# Require ZIPFILE to be set.
install_module() {  
  # Don't install module if not in boot mode.
  "$BOOTMODE" || abort "[Error] Installation not supported in recovery!"

  # Do necessary stuff.
  setup_flashable
  "$KSU" || mount_partitions
  api_level_arch_detect

  # Unzip module.
  unzip -o "$ZIPFILE" -d "$MODPATH" >&2
  
  # Load setup script.
  [[ -f "$MODPATH/setup.sh" ]] && source "$MODPATH/setup.sh"
  
  # Enable debugging if true.
  "$DEBUG" && set -x || set +x  

  # Remove all old files before doing installation if want to.
  "$CLEANSERVICE" && rm -rf "/data/adb/modules/$MODID"
 
  # Print module info.
  "$KSU" || info_print

  # Auto volume key selector load.
  "$AUTOVKSEL" && load_vksel

  # Initialize main.
  init_main

  # Skip mount.
  "$SKIPMOUNT" && touch "$MODPATH/skip_mount"  

  # Default permissions.
  set_perm_recursive "$MODPATH" 0 0 0755 0644
  set_perm_recursive "$MODPATH/system/bin" 0 2000 0755 0755
  set_perm_recursive "$MODPATH/system/xbin" 0 2000 0755 0755
  set_perm_recursive "$MODPATH/system/system_ext/bin" 0 2000 0755 0755
  set_perm_recursive "$MODPATH/system/vendor/bin" 0 2000 0755 0755 u:object_r:vendor_file:s0
    
  # Custom permissions.
  set_permissions    

  # Handle replace folders.
  "$KSU" || {
    for TARGET in "$REPLACE"; do
      ui_print "[Info] Replace target: $TARGET"
      mktouch "$MODPATH$TARGET/.replace"
    done
  }

  # Update module info.
  mktouch "$NVBASE/modules/$MODID/update"
  rm -rf "$NVBASE/modules/$MODID/remove" 2>/dev/null
  rm -rf "$NVBASE/modules/$MODID/disable" 2>/dev/null
  cp -af "$MODPATH/module.prop" "$NVBASE/modules/$MODID/module.prop"

  # Copy over custom sepolicy rules.
  "$KSU" || {
    [[ -f "$MODPATH/sepolicy.rule" ]] && {
      ui_print "[Info] Installing custom sepolicy rules"
      copy_preinit_files
    }
  }

  # Remove stuff that doesn't belong to modules and clean up any empty directories.
  rm -rf \
  "$MODPATH/META-INF" \
  "$MODPATH/addon" \
  "$MODPATH/LICENSE" \  
  "$MODPATH/module_functions.sh" \  
  "$MODPATH/setup.sh" \
  "$MODPATH/changelog.md" \
  "$MODPATH/README.md" \  
  "$MODPATH"/.git* \  
  "$MODPATH/system/placeholder"

  cd /
  rm -rf "$TMPDIR"
}

##########
# Presets
##########

# Detect whether in boot mode.
[[ -z "$BOOTMODE" ]] && ps | grep zygote | grep -qv grep && BOOTMODE=true
[[ -z "$BOOTMODE" ]] && ps -A 2>/dev/null | grep zygote | grep -qv grep && BOOTMODE=true
[[ -z "$BOOTMODE" ]] && BOOTMODE=false

TMPDIR=/dev/tmp
set_nvbase "/data/adb"