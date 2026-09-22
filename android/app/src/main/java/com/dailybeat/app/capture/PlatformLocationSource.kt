package com.dailybeat.app.capture

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import androidx.core.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Platform-only location for devices without Google services. No sensor or network SDK is bundled. */
@SuppressLint("MissingPermission")
class PlatformLocationSource(private val context: Context, private val preferGps: Boolean = false) : LocationSource {
    private val manager = context.getSystemService(LocationManager::class.java)
    private var listener: LocationListenerCompat? = null
    private var generation = 0

    private fun provider(): String? {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val choices = if (fine && preferGps) listOf(LocationManager.GPS_PROVIDER, "fused", LocationManager.NETWORK_PROVIDER)
            else if (fine) listOf("fused", LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            else listOf("fused", LocationManager.NETWORK_PROVIDER)
        return choices.firstOrNull { provider ->
            manager != null && provider in manager.allProviders && manager.isProviderEnabled(provider)
        }
    }

    override fun start(profile: ActiveCaptureProfile, onLocations: (List<Location>) -> Unit,
              onReady: () -> Unit, onError: (Exception) -> Unit) {
        stop()
        val epoch = generation
        val source = provider() ?: run { onError(IllegalStateException("Enable device location to capture your day.")); return }
        val next = object : LocationListenerCompat {
            override fun onLocationChanged(location: Location) {
                if (epoch == generation) onLocations(listOf(location))
            }
            override fun onLocationChanged(locations: MutableList<Location>) {
                if (epoch == generation) onLocations(locations)
            }
            override fun onProviderDisabled(provider: String) {
                if (epoch != generation) return
                stop()
                onError(IllegalStateException("Device location was turned off."))
            }
        }
        listener = next
        val request = LocationRequestCompat.Builder(profile.intervalMs)
            .setQuality(LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY)
            .setMinUpdateIntervalMillis(profile.minIntervalMs)
            .setMinUpdateDistanceMeters(profile.minDistanceM)
            .setMaxUpdateDelayMillis(profile.maxDelayMs).build()
        LocationManagerCompat.requestLocationUpdates(manager, source, request,
            ContextCompat.getMainExecutor(context), next)
        onReady()
    }

    override fun stop() {
        generation++
        // Compat uses a transport listener on older Android releases. Removing the original
        // listener directly leaves that registration (and its sensor work) alive.
        listener?.let { if (manager != null) LocationManagerCompat.removeUpdates(manager, it) }
        listener = null
    }

    override suspend fun current(): Location? {
        val source = provider() ?: return null
        val cancellation = CancellationSignal()
        return try {
            withTimeoutOrNull(15_000L) {
                suspendCancellableCoroutine { continuation ->
                    continuation.invokeOnCancellation { cancellation.cancel() }
                    LocationManagerCompat.getCurrentLocation(manager, source, cancellation,
                        ContextCompat.getMainExecutor(context)) { location ->
                        if (continuation.isActive) continuation.resume(location)
                    }
                }
            }
        } finally { cancellation.cancel() }
    }
}
