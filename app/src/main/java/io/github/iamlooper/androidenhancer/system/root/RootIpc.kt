package io.github.iamlooper.androidenhancer.system.root

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.topjohnwu.superuser.ipc.RootService as SuRootService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first

/**
 * Simple IPC facade.
 * Manages service connection state through serviceFlow.
 */
object RootIpc : ServiceConnection {

    private lateinit var appContext: Context
    private val serviceFlow = MutableStateFlow<IAndroidEnhancerService?>(null)

    fun init(context: Context, daemon: Boolean = true) {
        appContext = context.applicationContext
        val intent = Intent(appContext, RootService::class.java)
        if (daemon) {
            intent.addCategory(SuRootService.CATEGORY_DAEMON_MODE)
        }
        SuRootService.bind(intent, this)
    }

    override fun onServiceConnected(name: ComponentName, binder: IBinder) {
        serviceFlow.value = IAndroidEnhancerService.Stub.asInterface(binder)
    }

    override fun onServiceDisconnected(name: ComponentName) {
        serviceFlow.value = null
    }

    private suspend fun awaitService(): IAndroidEnhancerService? {
        return serviceFlow.value ?: serviceFlow.first { it != null }
    }

    suspend fun <R> invoke(block: suspend (IAndroidEnhancerService) -> R): R? {
        return awaitService()?.let { block(it) }
    }
}


