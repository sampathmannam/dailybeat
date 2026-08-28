# DailyBeat release shrink rules
-keep class com.dailybeat.app.data.model.** { *; }
-keep class com.dailybeat.app.data.db.** { *; }
-keep class com.google.mediapipe.** { *; }

-dontwarn com.google.mediapipe.proto.**
-dontwarn javax.lang.model.**
-dontwarn autovalue.shaded.**
