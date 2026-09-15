package com.dailybeat.app.capture

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** All proprietary location types live in this source set, never in the FOSS compile graph. */
@SuppressLint("MissingPermission")
class LocationBackend(context: Context) {
    private val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)
    private var callback: LocationCallback? = null
    private var generation = 0

    fun start(profile: ActiveCaptureProfile, onLocations: (List<Location>) -> Unit,
              onReady: () -> Unit, onError: (Exception) -> Unit) {
        stop()
        val epoch = generation
        val next = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (generation == epoch) onLocations(result.locations)
            }
        }
        callback = next
        val request = LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, profile.intervalMs)
            .setMinUpdateIntervalMillis(profile.minIntervalMs)
            .setMinUpdateDistanceMeters(profile.minDistanceM)
            .setMaxUpdateDelayMillis(profile.maxDelayMs).build()
        client.requestLocationUpdates(request, next, Looper.getMainLooper())
            .addOnSuccessListener { if (generation == epoch) onReady() }
            .addOnFailureListener { if (generation == epoch) onError(it) }
    }

    fun stop() {
        generation++
        callback?.let { client.removeLocationUpdates(it) }
        callback = null
    }

    suspend fun current(): Location? {
        val cancellation = CancellationTokenSource()
        return try {
            withTimeoutOrNull(15_000L) {
                suspendCancellableCoroutine { continuation ->
                    continuation.invokeOnCancellation { cancellation.cancel() }
                    client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
                        .addOnSuccessListener { if (continuation.isActive) continuation.resume(it) }
                        .addOnFailureListener { if (continuation.isActive) continuation.resume(null) }
                }
            }
        } finally { cancellation.cancel() }
    }
}
