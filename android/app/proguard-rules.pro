# DailyBeat release shrink rules
-keep class com.dailybeat.app.data.model.** { *; }
-keep class com.dailybeat.app.data.db.** { *; }
-dontwarn javax.lang.model.**
-dontwarn autovalue.shaded.**
# PDFBox's JPEG-2000 image codec is optional; DSR import extracts text only.
-dontwarn com.gemalto.jp2.**
