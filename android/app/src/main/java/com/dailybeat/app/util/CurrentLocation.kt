package com.dailybeat.app.util

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * One fresh location fix, for saving the place the officer is standing in.
 *
 * Adding a named place used to mean typing latitude and longitude by hand — coordinates an officer
 * does not carry in their head, and the exact "type numbers into a control panel" pattern
 * PRODUCT.md rules out. This asks the fused provider for the current position instead. Capture
 * permission is already granted for the app to work, so this reuses it; if it is somehow missing,
 * or no fix arrives, the caller gets null and shows a plain retry message rather than a crash.
 */
@SuppressLint("MissingPermission") // guarded by PermissionHelper.hasLocation below
suspend fun fetchCurrentLocation(context: Context): Location? {
    if (!PermissionHelper.hasLocation(context)) return null
    val client = LocationServices.getFusedLocationProviderClient(context.applicationContext)
    val cancellation = CancellationTokenSource()
    return suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancellation.cancel() }
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellation.token)
            .addOnSuccessListener { location -> continuation.resume(location) }
            .addOnFailureListener { continuation.resume(null) }
    }
}
