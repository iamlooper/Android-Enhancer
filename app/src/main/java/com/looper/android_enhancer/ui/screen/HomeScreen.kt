package com.looper.android_enhancer.ui.screen

import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.looper.android.support.util.DialogUtil
import com.looper.android_enhancer.R
import com.looper.android_enhancer.ui.component.MultiSelectPreference
import com.looper.android_enhancer.ui.component.Preference
import com.looper.android_enhancer.ui.component.PreferenceEndItem
import com.looper.android_enhancer.viewmodel.HomeViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    snackbarHostState: SnackbarHostState,
    coroutineScope: CoroutineScope
) {
    val context = LocalContext.current

    var loadingDialog by remember { mutableStateOf<AlertDialog?>(null) }
    var showLoadingDialog by remember { mutableStateOf(false) }
    var showRootCheckDialog by remember { mutableStateOf(false) }

    val selectedTweaks = viewModel.selectedTweaks.collectAsState()
    val focusedAppOptimizerEnabled = viewModel.focusedAppOptimizerEnabled.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.checkRootAccess { hasRoot ->
            if (!hasRoot) {
                showRootCheckDialog = true
            }
        }
    }

    LazyColumn(
        modifier = Modifier.padding(16.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.tweaks),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        item {
            Preference(
                title = stringResource(R.string.all_tweaks),
                description = stringResource(R.string.all_tweaks_summary),
                icon = ImageVector.vectorResource(R.drawable.ic_rocket_launch),
                onClick = {
                    coroutineScope.launch {
                        showLoadingDialog = true
                        delay(2000) // Wait a bit
                        viewModel.executeAndroidEnhancer("-a", true) {
                            viewModel.enableFocusedAppOptimizr() // Enable focused app optimizer
                            showLoadingDialog = false
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    message = context.getString(R.string.executed_the_option_in_background),
                                    withDismissAction = true
                                )
                            }
                        }
                    }
                }
            )
        }

        item {
            Preference(
                title = stringResource(R.string.main_tweak),
                description = stringResource(R.string.core_tweak_that_includes___),
                icon = ImageVector.vectorResource(R.drawable.ic_electric_bolt),
                onClick = {
                    coroutineScope.launch {
                        showLoadingDialog = true
                        viewModel.executeAndroidEnhancer("-m") {
                            showLoadingDialog = false
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    message = context.getString(R.string.executed_the_option),
                                    withDismissAction = true
                                )
                            }
                        }
                    }
                }
            )
        }

        item {
            Preference(
                title = stringResource(R.string.priority_tweak),
                description = stringResource(R.string.adjust_priorities_of_core___),
                icon = ImageVector.vectorResource(R.drawable.ic_process_chart),
                onClick = {
                    coroutineScope.launch {
                        showLoadingDialog = true
                        viewModel.executeAndroidEnhancer("-p") {
                            showLoadingDialog = false
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    message = context.getString(R.string.executed_the_option),
                                    withDismissAction = true
                                )
                            }
                        }
                    }
                }
            )
        }

        item {
            Preference(
                title = stringResource(R.string.art_tweak),
                description = stringResource(R.string.optimize_android_runtime___),
                icon = ImageVector.vectorResource(R.drawable.ic_apk_document),
                onClick = {
                    coroutineScope.launch {
                        showLoadingDialog = true
                        delay(2000) // Wait a bit
                        viewModel.executeAndroidEnhancer("-r", true) {
                            showLoadingDialog = false
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(
                                    message = context.getString(R.string.executed_the_option_in_background),
                                    withDismissAction = true
                                )
                            }
                        }
                    }
                }
            )
        }

        item {
            Preference(
                title = stringResource(R.string.focused_app_optimizer),
                description = stringResource(R.string.optimize_the_focused_app___),
                icon = ImageVector.vectorResource(R.drawable.ic_monitoring),
                endItem = PreferenceEndItem.Switch(
                    checked = focusedAppOptimizerEnabled.value,
                    onCheckedChange = { newState ->
                        coroutineScope.launch {
                            showLoadingDialog = true
                            delay(2000) // Wait a bit
                            viewModel.toggleFocusedAppOptimizer(newState) {
                                showLoadingDialog = false
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(
                                        message = context.getString(R.string.executed_the_option),
                                        withDismissAction = true
                                    )
                                }
                            }
                        }
                    }
                )
            )
        }

        item {
            Text(
                text = stringResource(R.string.settings),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        }

        item {
            MultiSelectPreference(
                title = stringResource(R.string.tweaks_on_boot),
                description = stringResource(R.string.select_tweaks_to_execute_on_boot),
                icon = ImageVector.vectorResource(R.drawable.ic_restart_alt),
                entries = stringArrayResource(R.array.pref_tweaks_on_boot_entries).toList(),
                entryValues = stringArrayResource(R.array.pref_tweaks_on_boot_entry_values).toList(),
                selectedValues = selectedTweaks.value,
                onValuesChanged = { newValues ->
                    viewModel.updateSelectedTweaks(newValues)
                }
            )
        }
    }

    if (showLoadingDialog) {
        loadingDialog = DialogUtil.displayLoadingProgressDialog(context)
    } else {
        loadingDialog?.dismiss()
        loadingDialog = null
    }

    if (showRootCheckDialog) {
        AlertDialog(
            onDismissRequest = { showRootCheckDialog = false },
            title = { Text(stringResource(R.string.notice)) },
            text = { Text(stringResource(R.string.no_root_permission)) },
            confirmButton = {
                TextButton(onClick = { showRootCheckDialog = false }) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }
}