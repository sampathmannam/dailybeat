package com.dailybeat.app.ui.theme

import androidx.compose.ui.graphics.Color

// DailyBeat: a neutral field surface, midnight structure and one high-visibility living trail.
val Ink = Color(0xFF0B1B33)
val InkMuted = Color(0xFF475569)
val Navy = Color(0xFF0B2D5B)
val NavySoft = Color(0xFF294E78)
val Gold = Color(0xFFFFD60A)
val GoldSoft = Color(0xFFFFF2A8)
val Canvas = Color(0xFFF8FAFC)
val SurfaceCard = Color(0xFFFFFFFF)
val SurfaceElevated = Color(0xFFEEF3F8)

// 3.39:1 on white and 3.26:1 on the canvas — the WCAG floor for a control boundary is 3:1. The
// previous #CBD5E1 measured 1.48:1, which left every unfocused text field with no visible box.
val OutlineSoft = Color(0xFF7C8DA3)
val ErrorRed = Color(0xFFB3261E)
val SuccessGreen = Color(0xFF0F6F46)
val WarningAmber = Color(0xFF985A00)
val WarningAmberSoft = Color(0xFFFFE7BF)
val WarningAmberInk = Color(0xFF4A2B00)
val NightCanvas = Color(0xFF081A2E)
val NightSurface = Color(0xFF10263D)
val NightElevated = Color(0xFF183149)
val NightInk = Color(0xFFF2F6FA)
val NightMuted = Color(0xFFB7C5D4)
val NightAmber = Color(0xFFF2B45E)
val NightAmberSoft = Color(0xFF5C3A00)
val NightAmberInk = Color(0xFFFFE1B3)

// Event type accents (timeline). Two sets: the light ones sit at 5.5–7.2:1 on white but fall to
// 2.1–2.8:1 on the night surface, so dark theme gets its own lifted set. Visit is teal so it can no
// longer be confused with the GPS green — they measured 1.12:1 apart.
val EventManual = Color(0xFF175EA8)
val EventVoice = Color(0xFF6941A5)
val EventGps = Color(0xFF0F6F46)
val EventCall = Color(0xFF985A00)
val EventVisit = Color(0xFF0B6B75)
val EventMoment = Color(0xFF9B336B)

val EventManualNight = Color(0xFF8DBDF7)
val EventVoiceNight = Color(0xFFC9AEF5)
val EventGpsNight = Color(0xFF6FD6A3)
val EventCallNight = Color(0xFFF5BE6B)
val EventVisitNight = Color(0xFF5FD5D0)
val EventMomentNight = Color(0xFFF39BC9)
