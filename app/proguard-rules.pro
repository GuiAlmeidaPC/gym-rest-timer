# Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.* { *; }
-keepclasseswithmembernames class * { @dagger.hilt.* <methods>; }

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-dontwarn androidx.room.paging.**

# Kotlin metadata
-keepattributes RuntimeVisible*Annotations,InnerClasses,Signature,Exceptions

# App entities accessed reflectively by Room
-keep class com.gymresttimer.data.** { *; }
