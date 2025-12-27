package io.github.iamlooper.androidenhancer.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.res.painterResource
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import io.github.iamlooper.androidenhancer.R
import io.github.iamlooper.androidenhancer.ui.components.LoadingIndicatorDialog
import kotlinx.coroutines.delay
import io.github.iamlooper.androidenhancer.ui.screens.about.AboutScreen
import io.github.iamlooper.androidenhancer.ui.screens.about.AboutViewModel
import io.github.iamlooper.androidenhancer.ui.screens.home.HomeScreen
import io.github.iamlooper.androidenhancer.ui.screens.home.HomeViewModel
import io.github.iamlooper.androidenhancer.ui.screens.log.LogScreen
import io.github.iamlooper.androidenhancer.ui.screens.log.LogViewModel
import io.github.iamlooper.androidenhancer.ui.screens.per_app_mode.PerAppModeScreen
import io.github.iamlooper.androidenhancer.ui.screens.per_app_mode.PerAppModeViewModel
import io.github.iamlooper.androidenhancer.ui.screens.settings.SettingsScreen
import io.github.iamlooper.androidenhancer.ui.screens.settings.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppNavHost(
    navController: NavHostController,
    snackbarHostState: SnackbarHostState
) {
    val scope = rememberCoroutineScope()
    
    val homeViewModel: HomeViewModel = viewModel()
    val logViewModel: LogViewModel = viewModel()
    val aboutViewModel: AboutViewModel = viewModel()
    val modeChangeViewModel: PerAppModeViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()

    val aboutState by aboutViewModel.state.collectAsStateWithLifecycle()
    val logState by logViewModel.state.collectAsStateWithLifecycle()
    val modeChangeState by modeChangeViewModel.state.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.state.collectAsStateWithLifecycle()
    val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
    
    var showLoading by remember { mutableStateOf(false) }

    // Reset loading state after 3 seconds
    LaunchedEffect(showLoading) {
        if (showLoading) {
            delay(3000)
            showLoading = false
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: AppDestination.Home.route

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (currentRoute) {
                            AppDestination.Home.route -> stringResource(R.string.app_name)
                            AppDestination.Log.route -> stringResource(R.string.log)
                            AppDestination.About.route -> stringResource(R.string.about)
                            AppDestination.PerAppMode.route -> stringResource(R.string.per_app_mode)
                            AppDestination.Settings.route -> stringResource(R.string.settings)
                            else -> stringResource(R.string.app_name)
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                navigationIcon = {
                    if (navController.previousBackStackEntry != null && currentRoute != AppDestination.Home.route) {
                        IconButton(onClick = { navController.navigateUp() }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_arrow_back),
                                contentDescription = stringResource(R.string.navigate_back)
                            )
                        }
                    }
                },
                actions = {
                    if (currentRoute == AppDestination.Home.route) {
                        Switch(
                            checked = homeState.serviceEnabled,
                            onCheckedChange = {
                                showLoading = true
                                homeViewModel.toggleService(it)
                            }
                        )
                        IconButton(onClick = { navController.navigate(AppDestination.Settings.route) }) {
                            Icon(painter = painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings))
                        }
                        IconButton(onClick = { navController.navigate(AppDestination.About.route) }) {
                            Icon(painter = painterResource(R.drawable.ic_info), contentDescription = stringResource(R.string.about))
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = AppDestination.Home.route,
            modifier = Modifier.padding(padding)
        ) {
            composable(AppDestination.Home.route) {
                HomeScreen(
                    state = homeState,
                    onModeSelected = homeViewModel::setMode,
                    onOpenLog = { navController.navigate(AppDestination.Log.route) },
                    onOpenPerAppMode = { navController.navigate(AppDestination.PerAppMode.route) }
                )
            }
            composable(AppDestination.Log.route) {
                LogScreen(
                    state = logState,
                    onShare = { context ->
                        logViewModel.shareLog(context) { message ->
                            scope.launch { snackbarHostState.showSnackbar(message) }
                        }
                    },
                    onClear = {
                        logViewModel.clearLog { message ->
                            scope.launch { snackbarHostState.showSnackbar(message) }
                        }
                    }
                )
            }
            composable(AppDestination.About.route) {
                AboutScreen(state = aboutState)
            }
            composable(AppDestination.Settings.route) {
                SettingsScreen(
                    state = settingsState,
                    onStartOnBootChanged = settingsViewModel::setStartOnBoot,
                    onTouchBoostEnabledChanged = settingsViewModel::setTouchBoostEnabled,
                    onLanguageModeChanged = settingsViewModel::setLanguageMode,
                    onThemeModeChanged = settingsViewModel::setThemeMode,
                    onPureBlackThemeChanged = settingsViewModel::setPureBlackTheme,
                    onUseDynamicThemeChanged = settingsViewModel::setUseDynamicTheme
                )
            }
            composable(AppDestination.PerAppMode.route) {
                PerAppModeScreen(
                    state = modeChangeState,
                    onSetModeOverride = modeChangeViewModel::setModeOverride,
                    onRemoveModeOverride = modeChangeViewModel::removeModeOverride,
                )
            }
        }
    }
    
    LoadingIndicatorDialog(visible = showLoading)
}

