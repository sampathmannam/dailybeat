package com.dailybeat.app.capture

import android.content.Context

/** Platform-only backend; no proprietary SDK is in the FOSS compile graph. */
class LocationBackend(context: Context) : LocationSource by PlatformLocationSource(context)
