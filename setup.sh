#!/sbin/sh

###########################
# MMT Reborn Setup
###########################

############
# Config Vars
############

# Set this to true if you don't want to mount the system folder in your module.
SKIPMOUNT=true
# Set this to true if you want to clean old files in module before flashing new module.
CLEANSERVICE=false
# Set this to true if you want to load vskel after module info print. If you want to manually load it, consider using load_vksel function.
AUTOVKSEL=false
# Set this to true if you want to debug the installation.
DEBUG=true

############
# Replace List
############

# List all directories you want to directly replace in the system.
# Construct your list in the following example format.
REPLACE_EXAMPLE="
/system/app/Youtube
/system/priv-app/SystemUI
/system/priv-app/Settings
/system/framework
"
# Construct your own list here.
REPLACE="
"

############
# Permissions
############

# Set permissions.
set_permissions() {
  set_perm_recursive "$MODPATH/libs" 0 0 0777 0755
}

############
# Info Print
############

# Set what you want to be displayed on header of installation process.
info_print() {
  ui_print ""
  print_title "Android Enhancer" "By @iamlooper @ telegram"

  sleep 2
}

############
# Main
############

# Change the logic to whatever you want.
init_main() {
  ui_print ""
  ui_print "[Info] Installing Android Enhancer..."
  ui_print ""

  # Configure native libraries.
  configure_native_libs

  sleep 2
  
  print_title "Notes"
  ui_print ""
  ui_print "[Info] Reboot is required"
  ui_print ""
  ui_print "[Info] Join loopprojects @ Telegram to get new updates"
  ui_print ""  
  ui_print "[Info] Report issues to loopchats @ Telegram"
  ui_print ""
  ui_print "[Info] You can find me at iamlooper @ Telegram for direct support"
  ui_print ""  

  sleep 2
}