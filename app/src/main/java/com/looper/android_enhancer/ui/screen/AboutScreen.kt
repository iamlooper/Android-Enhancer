package com.looper.android_enhancer.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.google.android.gms.oss.licenses.OssLicensesMenuActivity
import com.looper.android.support.util.AppUtil
import com.looper.android_enhancer.R
import com.looper.android_enhancer.ui.component.Preference
import com.looper.android_enhancer.ui.component.PreferenceEndItem

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Preference(
            title = stringResource(R.string.developer),
            description = stringResource(R.string.looper),
            icon = ImageVector.vectorResource(R.drawable.ic_manage_accounts),
            endItem = PreferenceEndItem.Arrow,
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/iamlooper"))
                context.startActivity(intent)
            }
        )

        Preference(
            title = stringResource(R.string.version),
            description = AppUtil.getAppVersionName(context),
            icon = ImageVector.vectorResource(R.drawable.ic_extension)
        )

        Preference(
            title = stringResource(R.string.release_channel),
            description = stringResource(R.string.checkout_new_projects___),
            icon = ImageVector.vectorResource(R.drawable.ic_new_releases),
            endItem = PreferenceEndItem.Arrow,
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/loopprojects"))
                context.startActivity(intent)
            }
        )

        Preference(
            title = stringResource(R.string.credits),
            description = stringResource(R.string.much_appreciated_people___),
            icon = ImageVector.vectorResource(R.drawable.ic_groups),
            endItem = PreferenceEndItem.Arrow,
            onClick = {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/iamlooper/Android-Enhancer/tree/main#credits-")
                )
                context.startActivity(intent)
            }
        )

        Preference(
            title = stringResource(R.string.licenses),
            description = stringResource(R.string.view_the_open_source_licenses),
            icon = ImageVector.vectorResource(R.drawable.ic_license),
            endItem = PreferenceEndItem.Arrow,
            onClick = {
                context.startActivity(Intent(context, OssLicensesMenuActivity::class.java))
            }
        )
    }
}