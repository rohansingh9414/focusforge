# FocusForge ProGuard / R8 Rules
# Minimal, targeted rules for release optimization

# Room database implementation classes (fallback in case consumer rules are stripped)
-keep class * extends androidx.room.RoomDatabase {
    <init>();
}

# WorkManager Workers
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
