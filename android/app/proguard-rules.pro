# DailyBeat release shrink rules
-keep class com.dailybeat.app.data.model.** { *; }
-keep class com.dailybeat.app.data.db.** { *; }
-dontwarn javax.lang.model.**
-dontwarn autovalue.shaded.**

# src/main currently contains no android.util.Log call at all, and a police officer's coordinates
# and diary text must never reach logcat. Strip the calls at shrink time so a future one added
# without thinking cannot survive into a signed release.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
    public static int wtf(...);
}
