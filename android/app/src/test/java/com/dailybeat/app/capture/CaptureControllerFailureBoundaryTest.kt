package com.dailybeat.app.capture

import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.dailybeat.app.DailyBeatApp
import com.dailybeat.app.audit.OperationalFailureLog
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = DailyBeatApp::class)
class CaptureControllerFailureBoundaryTest {

    private lateinit var context: DailyBeatApp

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        OperationalFailureLog.clear(context)
    }

    @After
    fun tearDown() {
        OperationalFailureLog.clear(context)
    }

    @Test
    fun locationStartReturnsFailureInsteadOfSuppressingIllegalStateException() {
        val restrictedContext = object : ContextWrapper(context) {
            override fun startForegroundService(service: Intent): ComponentName? {
                throw IllegalStateException("model-derived [V999]")
            }
        }

        val result = LocationService.start(restrictedContext)

        assertTrue(result.isFailure)
    }

    @Test
    fun locationStartStillStartsForegroundServiceOnSuccess() {
        var started = false
        val acceptingContext = object : ContextWrapper(context) {
            override fun startForegroundService(service: Intent): ComponentName? {
                started = true
                return ComponentName(context.packageName, LocationService::class.java.name)
            }
        }

        val result = LocationService.start(acceptingContext)

        assertTrue(result.isSuccess)
        assertTrue(started)
    }

    @Test
    fun callLogScheduleAndCancelReturnFailuresInsteadOfSuppressingThem() {
        val schedule = CallLogWorker.schedule(context) {
            throw IllegalStateException("schedule model-derived [V999]")
        }
        val cancel = CallLogWorker.cancel(context) {
            throw IllegalStateException("cancel diary=private call content")
        }

        assertTrue(schedule.isFailure)
        assertTrue(cancel.isFailure)
    }

    @Test
    fun startAndScheduleFailuresPersistOnlyFixedMessagesAndBothOperationsRun() {
        var callLogAttempted = false

        CaptureController.applyCaptureOperations(
            context = context,
            startGps = true,
            scheduleCallLog = true,
            gpsOperation = {
                Result.failure(IllegalStateException("prompt=model-derived [V999]"))
            },
            callLogOperation = {
                callLogAttempted = true
                Result.failure(IllegalStateException("diary=private call content 555-1234"))
            },
        )

        val lines = OperationalFailureLog.readRecent(context)

        assertTrue(callLogAttempted)
        assertEquals(2, lines.size)
        assertTrue(lines.single { it.contains("capture-gps") }.endsWith("GPS capture start failed."))
        assertTrue(
            lines.single { it.contains("capture-call-log") }
                .endsWith("Call-log scheduling failed."),
        )
        listOf("[V999]", "model-derived", "private call content", "555-1234").forEach { sensitive ->
            assertFalse(lines.any { it.contains(sensitive) })
        }
    }

    @Test
    fun cancelFailurePersistsFixedMessageWithoutArbitraryExceptionText() {
        CaptureController.applyCaptureOperations(
            context = context,
            startGps = false,
            scheduleCallLog = false,
            gpsOperation = { Result.success(Unit) },
            callLogOperation = {
                Result.failure(IllegalStateException("Bearer private-token [E404]"))
            },
        )

        val line = OperationalFailureLog.readRecent(context).single()

        assertTrue(line.contains("capture-call-log"))
        assertTrue(line.endsWith("Call-log cancellation failed."))
        assertFalse(line.contains("private-token"))
        assertFalse(line.contains("[E404]"))
    }
}
