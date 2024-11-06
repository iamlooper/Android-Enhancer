package com.looper.android_enhancer.activity

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jaredrummler.ktsh.Shell
import com.looper.android.support.provider.AppUpdateProvider
import com.looper.android.support.util.PermissionUtil
import com.looper.android_enhancer.MyApp
import com.looper.android_enhancer.R
import com.looper.android_enhancer.ui.screen.AboutScreen
import com.looper.android_enhancer.ui.screen.HomeScreen
import com.looper.android_enhancer.ui.screen.LogScreen
import com.looper.android_enhancer.ui.theme.AndroidEnhancerTheme
import com.looper.android_enhancer.viewmodel.HomeViewModel
import com.looper.android_enhancer.viewmodel.LogViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request for notification permission on A13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            PermissionUtil.requestPermission(
                activity = this,
                context = this,
                permission = Manifest.permission.POST_NOTIFICATIONS
            ).invoke()
        }

        initializeRoot()

        lifecycleScope.launch {
            AppUpdateProvider.checkForUpdate(
                this@MainActivity,
                "https://raw.githubusercontent.com/iamlooper/Android-Enhancer-App/main/update.json"
            )
        }

        setContent {
            AndroidEnhancerTheme {
                Content()
            }
        }
    }
}

private fun initializeRoot() {
    try {
        MyApp.shell = Shell.SU
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Content() {
    val navController = rememberNavController()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    val logViewModel: LogViewModel = viewModel()
    val homeViewModel: HomeViewModel = viewModel()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (currentRoute) {
                            "home" -> stringResource(R.string.app_name)
                            "log" -> stringResource(R.string.log)
                            "about" -> stringResource(R.string.about)
                            else -> stringResource(R.string.app_name)
                        }
                    )
                },
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    if (currentRoute != "home") {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                actions = {
                    when (currentRoute) {
                        "home" -> HomeScreenActions(navController)
                        "log" -> LogScreenActions(
                            logViewModel = logViewModel,
                            snackbarHostState = snackbarHostState,
                            coroutineScope = scope
                        )

                        else -> {}
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(paddingValues)
        ) {
            composable("home") {
                HomeScreen(
                    viewModel = homeViewModel,
                    snackbarHostState = snackbarHostState,
                    coroutineScope = scope
                )
            }
            composable("log") {
                LogScreen(
                    viewModel = logViewModel,
                    coroutineScope = scope
                )
            }
            composable("about") {
                AboutScreen()
            }
        }
    }
}

@Composable
private fun HomeScreenActions(navController: NavHostController) {
    IconButton(onClick = { navController.navigate("log") }) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_description),
            contentDescription = "Log"
        )
    }
    IconButton(onClick = { navController.navigate("about") }) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_info),
            contentDescription = "About"
        )
    }
}

@Composable
private fun LogScreenActions(
    logViewModel: LogViewModel,
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope
) {
    val context = LocalContext.current

    IconButton(
        onClick = {
            logViewModel.shareLog(context) { message ->
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = message,
                        withDismissAction = true
                    )
                }
            }
        }
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_share),
            contentDescription = "Share"
        )
    }
    IconButton(
        onClick = {
            logViewModel.clearLog { message ->
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = message,
                        withDismissAction = true
                    )
                }
            }
        }
    ) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_delete),
            contentDescription = "Clear"
        )
    }
}