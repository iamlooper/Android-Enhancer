package com.looper.android_enhancer.activity

import android.os.Bundle
import android.Manifest
import android.os.Build
import android.view.Window
import android.view.View
import android.content.Context

import androidx.navigation.NavController
import androidx.navigation.NavDestination

import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.appbar.AppBarLayout

import com.looper.android.support.activity.BottomNavigationActivity
import com.looper.android.support.util.UIUtils
import com.looper.android.support.util.PermissionUtils

import com.looper.android_enhancer.R

class MainActivity : BottomNavigationActivity(), NavController.OnDestinationChangedListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Setup navigation.   
        setupNavigation(R.navigation.mobile_navigation, R.menu.bottom_nav_menu)
        
        // Add destination changed listener.
        navController.addOnDestinationChangedListener(this@MainActivity)
                              
        // Request for notification permission on A13+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionUtils.request(this, this, Manifest.permission.POST_NOTIFICATIONS)
        }        
    }  
    
    override fun onDestinationChanged(
        controller: NavController,
        destination: NavDestination,
        arguments: Bundle?
    ) {
        val appbar: AppBarLayout = findViewById(com.looper.android.support.R.id.appbar)
               
        if (destination.id == R.id.fragment_log) {
            appbar.liftOnScrollTargetViewId = R.id.log_recycler_view
        } else {
            appbar.liftOnScrollTargetViewId = android.R.id.content
        }
    }
}
