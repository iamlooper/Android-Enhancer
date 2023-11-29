package com.looper.android_enhancer.fragment

import android.os.Bundle

import com.looper.android.support.preference.PreferenceFragment

import com.looper.android_enhancer.R

class AboutFragment : PreferenceFragment() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.about_preferences, rootKey)
    }
}