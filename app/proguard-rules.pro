# Project specific R8 rules. Room, WorkManager, Compose, Coil, Haze and
# kotlinx.coroutines ship their own consumer rules; activities, receivers and
# providers are kept through the manifest.

# Keep line numbers for readable crash reports; the mapping file restores names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# BuildConfig fields are read at runtime (version, debug flags).
-keep class app.brokoli5191.quote.BuildConfig { *; }

# WorkManager instantiates workers by class name through reflection, using the
# (Context, WorkerParameters) constructor. Persisted work requests store the
# class name, so it must stay stable across updates.
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

