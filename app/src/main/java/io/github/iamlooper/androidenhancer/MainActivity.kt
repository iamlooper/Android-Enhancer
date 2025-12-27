package io.github.iamlooper.androidenhancer

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.topjohnwu.superuser.Shell
import dagger.hilt.android.AndroidEntryPoint
import io.github.iamlooper.androidenhancer.data.local.PreferencesSnapshot
import io.github.iamlooper.androidenhancer.data.local.appDataStore
import io.github.iamlooper.androidenhancer.data.local.snapshotFlow
import io.github.iamlooper.androidenhancer.ui.navigation.AppNavHost
import io.github.iamlooper.androidenhancer.ui.theme.AppTheme
import javax.inject.Inject
import io.github.iamlooper.androidenhancer.data.repository.AppRepository
import io.github.iamlooper.androidenhancer.system.root.RootIpc

@OptIn(ExperimentalMaterial3Api::class)
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var repository: AppRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        setContent {
            AppRoot(repository)
        }
    }
}

@Composable
private fun AppRoot(repository: AppRepository) {
    val context = LocalContext.current
    val dataStore = remember { context.appDataStore }
    val preferencesFlow = remember { dataStore.snapshotFlow() }
    val preferences by preferencesFlow.collectAsState(initial = PreferencesSnapshot())

    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (preferences.themeMode) {
        0 -> systemDark
        1 -> false
        2 -> true
        else -> systemDark
    }

    AppTheme(
        darkTheme = darkTheme,
        dynamicColor = preferences.useDynamicTheme,
        pureBlack = preferences.pureBlackTheme
    ) {
        val navController = rememberNavController()
        val snackbarHostState = remember { SnackbarHostState() }
        val showRootErrorDialog = remember { mutableStateOf(false) }

        // Request notification permission on Android 13+
        val permissionLauncher = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
                onResult = { /* Handle permission result if needed */ }
            )
        } else {
            null
        }

        LaunchedEffect(Unit) {
            val isRoot = withContext(Dispatchers.IO) {
                try {
                    // Block until shell is acquired
                    Shell.getShell().isRoot
                } catch (_: Exception) {
                    false
                }
            }

            if (isRoot) {
                // Ensure IPC is initialized after root is granted
                RootIpc.init(context)
                // Only start service if it was previously enabled by the user
                if (preferences.serviceEnabled) {
                    repository.startService()
                }
            } else {
                showRootErrorDialog.value = true
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionLauncher?.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (showRootErrorDialog.value) {
            RootAccessErrorDialog(
                onDismiss = {
                    showRootErrorDialog.value = false
                }
            )
        }

        AppNavHost(
            navController = navController,
            snackbarHostState = snackbarHostState
        )
    }
}

@Composable
private fun RootAccessErrorDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.root_access_not_found),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = stringResource(R.string.root_access_not_found_message),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = stringResource(R.string.dismiss),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}
