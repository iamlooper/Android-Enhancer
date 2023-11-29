package com.looper.android_enhancer.fragment

import java.io.File

import android.os.Bundle

import androidx.preference.Preference
import androidx.preference.SwitchPreferenceCompat

import com.looper.android.support.preference.PreferenceFragment

import com.topjohnwu.superuser.Shell

import com.looper.android.support.util.ShellUtils
import com.looper.android.support.util.AppUtils
import com.looper.android.support.util.UIUtils
import com.looper.android.support.util.SharedPreferenceUtils

import com.looper.android_enhancer.R

class HomeFragment : PreferenceFragment() {

    // Initialize variables.
    private lateinit var sharedPreferenceUtils: SharedPreferenceUtils
    private lateinit var memoryTweakSwitchPreference: SwitchPreferenceCompat
    private val logFilePath: String by lazy { File(requireContext().filesDir, "log.txt").toString() }
    private val androidEnhancerLibraryPath: String by lazy { AppUtils.getNativeLibraryPath(requireContext(), "android_enhancer") }
    private val vmTouchLibraryPath: String by lazy { AppUtils.getNativeLibraryPath(requireContext(), "vmtouch") }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        // Inflate preferences from XML resource file.
        setPreferencesFromResource(R.xml.home_preferences, rootKey)

        // Initialize SharedPreferenceUtils.
        sharedPreferenceUtils = SharedPreferenceUtils.getInstance(requireContext())

        // Find memory tweak switch preference and set its change listener.
        memoryTweakSwitchPreference = findPreference("switch_pref_memory_tweak")!!
        memoryTweakSwitchPreference.setOnPreferenceChangeListener { _, newValue ->
            val isChecked = newValue as Boolean
            if (isChecked) {
                // Enable memory preload tweak if the switch is checked.
                executeTweakCommand("--enable-memory-tweak", androidEnhancerLibraryPath, logFilePath)
            } else {
                // Disable memory preload tweak if the switch is unchecked.
                executeTweakCommand("--disable-memory-tweak", androidEnhancerLibraryPath, logFilePath)
            }
            
            // Show preference change if the app has root, otherwise don't show the change.
            if (Shell.isAppGrantedRoot() == true) {
                true
            } else {
                false
            }
        }
    }

    override fun onPreferenceTreeClick(preference: Preference): Boolean {
        // Handle different preferences based on their keys.
        return when (preference.key) {
            "pref_execute_all_tweaks" -> {
                // Execute command to apply all tweaks.
                executeTweakCommand("--all-tweaks", androidEnhancerLibraryPath, logFilePath)     
                true
            }
            "pref_main_tweak" -> {
                // Execute command to apply main tweak.
                executeTweakCommand("--main-tweak", androidEnhancerLibraryPath, logFilePath)
                true
            }
            "pref_dalvik_tweak" -> {
                // Execute command to apply dalvik tweak.
                executeTweakCommand("--dalvik-tweak", androidEnhancerLibraryPath, logFilePath)
                true
            }
            "pref_clean_junk" -> {
                // Execute command to clean junk.
                executeTweakCommand("--clean-junk", androidEnhancerLibraryPath, logFilePath)
                true
            }
            else -> super.onPreferenceTreeClick(preference)
        }
    }

    // Function to execute tweak command and show snackbar.
    private fun executeTweakCommand(command: String, androidEnhancerLibraryPath: String, logFilePath: String) {
        // Check if the app has root permission.
        if (Shell.isAppGrantedRoot() == true) {
            // Copy VMTouch library and set appropriate permissions.    
            ShellUtils.execute("cp -f $vmTouchLibraryPath /data/local/tmp/vmtouch")
            ShellUtils.execute("chmod 0777 $androidEnhancerLibraryPath")
            ShellUtils.execute("chmod 0777 /data/local/tmp/vmtouch")    
           
            // Execute shell command to apply tweak and log the output.
            ShellUtils.execute("$androidEnhancerLibraryPath $command 1>> $logFilePath &")
            
            // Toggle memory tweak switch if all tweaks are executed.
            if (command == "--all-tweaks") {
                memoryTweakSwitchPreference.isChecked = true
            }

            // Show snackbar.
            UIUtils.showSnackbarWithAction(
                requireView(),
                requireContext().getString(R.string.snackbar_tweak_executed),
                "Dismiss",
                UIUtils.SNACKBAR_LENGTH_SHORT
            ) { /* No operation */ }
        } else {
            // Show snackbar if the app doesn't have root permission.
            UIUtils.showSnackbarWithAction(
                requireView(),
                requireContext().getString(R.string.snackbar_no_root),
                "Dismiss",
                UIUtils.SNACKBAR_LENGTH_SHORT
            ) { /* No operation */ }
        }
    }
}