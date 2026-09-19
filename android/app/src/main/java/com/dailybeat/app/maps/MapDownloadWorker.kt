package com.dailybeat.app.maps

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope

class MapDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = coroutineScope {
        val repository = (applicationContext as DailyBeatApp).offlineMaps
        try {
            setForeground(foreground(0, 0))
            repository.install { done, total -> setForeground(foreground(done, total)) }
            Result.success()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { repository.failed(error); Result.failure() }
    }

    private fun foreground(done: Long, total: Long): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Offline map downloads", NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_dailybeat)
            .setContentTitle("Downloading Tamil Nadu map")
            .setContentText(if (total > 0) "${done * 100 / total}% · You can pause in Settings" else "Preparing download")
            .setOnlyAlertOnce(true).setOngoing(true)
            .setProgress(100, if (total > 0) (done * 100 / total).toInt() else 0, total == 0L).build()
        return if (Build.VERSION.SDK_INT >= 29) ForegroundInfo(7302, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else ForegroundInfo(7302, notification)
    }

    companion object { private const val CHANNEL = "offline_maps" }
}
