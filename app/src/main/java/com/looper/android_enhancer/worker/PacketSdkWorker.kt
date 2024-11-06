package com.looper.android_enhancer.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.packet.sdk.PacketSdk
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

class PacketSdkWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        PacketSdk.SetAppKey("JkhvZChcHpdlcOoE")
        PacketSdk.Start(applicationContext)

        delay(TimeUnit.MINUTES.toMillis(25))

        return Result.success()
    }

    companion object {
        fun startWorker(context: Context) {
            val workManager = WorkManager.getInstance(context)

            // Check if the work is already running
            workManager.getWorkInfosForUniqueWork("PacketSdkWork").get().let { workInfos ->
                val isWorkScheduled = workInfos.any { workInfo ->
                    workInfo.state == WorkInfo.State.ENQUEUED || workInfo.state == WorkInfo.State.RUNNING
                }

                if (!isWorkScheduled) {
                    val constraints = Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()

                    val packetSdkWorkRequest = PeriodicWorkRequestBuilder<PacketSdkWorker>(
                        PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS, TimeUnit.MILLISECONDS
                    )
                        .addTag("packet_sdk_worker")
                        .setConstraints(constraints)
                        .build()

                    workManager.enqueueUniquePeriodicWork(
                        "PacketSdkWork",
                        ExistingPeriodicWorkPolicy.KEEP,
                        packetSdkWorkRequest
                    )
                }
            }
        }
    }
}