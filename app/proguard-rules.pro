# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao interface *

# Coil
-keep class coil.** { *; }

# sing-box binary in assets — не трогать
-keep class com.android.cheburgate.core.SingBoxManager { *; }

# Kotlin coroutines
-keepnames class kotlinx.coroutines.** { *; }

# ML Kit barcode
-keep class com.google.mlkit.** { *; }

# CameraX
-keep class androidx.camera.** { *; }
