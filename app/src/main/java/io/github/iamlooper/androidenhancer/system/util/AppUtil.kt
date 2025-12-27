package io.github.iamlooper.androidenhancer.system.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.runtime.Immutable

@Immutable
data class InstalledApp(
    val packageName: String,
    val label: String
)

object AppUtil {
    fun installedLaunchableApps(context: Context): List<InstalledApp> {
        val packageManager = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfo = packageManager.queryIntentActivities(mainIntent, 0)
        return resolveInfo
            .map { info ->
                val label = info.loadLabel(packageManager).toString()
                InstalledApp(info.activityInfo.packageName, label)
            }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .sortedBy { it.label.lowercase() }
    }

    fun appVersionName(context: Context): String {
        return runCatching {
            val packageManager = context.packageManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                ).versionName ?: ""
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
            }
        }.getOrDefault("")
    }
}
