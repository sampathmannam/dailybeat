# DailyBeat release shrink rules
-keep class com.dailybeat.app.data.model.** { *; }
-keep class com.dailybeat.app.data.db.** { *; }
-dontwarn javax.lang.model.**
-dontwarn autovalue.shaded.**
