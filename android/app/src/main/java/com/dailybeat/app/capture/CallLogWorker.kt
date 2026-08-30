package com.dailybeat.app.capture

import android.content.Context
import android.provider.CallLog
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.data.model.Event
import com.dailybeat.app.util.PermissionHelper
import java.util.concurrent.TimeUnit

class CallLogWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as DailyBeatApp
        if (!app.settingsRepository.get().callLogEnabled) {
            return Result.success()
        }
        if (!PermissionHelper.hasCallLog(applicationContext)) {
            return Result.success()
        }

        val since = System.currentTimeMillis() - 15 * 60 * 1000
        val cursor = applicationContext.contentResolver.query(
            CallLog.Calls.CONTENT_URI,
            arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE, CallLog.Calls.DURATION, CallLog.Calls.DATE),
            "${CallLog.Calls.DATE} > ?",
            arrayOf(since.toString()),
            CallLog.Calls.DATE + " DESC",
        )

        val dao = app.db.events()
        cursor?.use { rows ->
            while (rows.moveToNext()) {
                val number = rows.getString(0) ?: "unknown"
                val type = rows.getInt(1)
                val duration = rows.getInt(2)
                val date = rows.getLong(3)
                if (dao.countBySource("call", number, date) > 0) continue

                val typeLabel = when (type) {
                    CallLog.Calls.INCOMING_TYPE -> "incoming"
                    CallLog.Calls.OUTGOING_TYPE -> "outgoing"
                    CallLog.Calls.MISSED_TYPE -> "missed"
                    else -> "call"
                }
                dao.insert(
                    Event(
                        timestamp = date,
                        type = "call",
                        rawText = "$typeLabel call to $number (${duration}s)",
                        sourceId = number,
                    ),
                )
            }
        }
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "call_log_poll"

        fun schedule(context: Context): kotlin.Result<Unit> =
            schedule(context) { WorkManager.getInstance(it) }

        internal fun schedule(
            context: Context,
            workManagerProvider: (Context) -> WorkManager,
        ): kotlin.Result<Unit> = runCatching {
            val request = PeriodicWorkRequestBuilder<CallLogWorker>(15, TimeUnit.MINUTES).build()
            workManagerProvider(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
            Unit
        }

        fun cancel(context: Context): kotlin.Result<Unit> =
            cancel(context) { WorkManager.getInstance(it) }

        internal fun cancel(
            context: Context,
            workManagerProvider: (Context) -> WorkManager,
        ): kotlin.Result<Unit> = runCatching {
            workManagerProvider(context).cancelUniqueWork(WORK_NAME)
            Unit
        }
    }
}
