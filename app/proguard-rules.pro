# Kotlinx Serialization -----------------------------------------------------
# The compiler plugin generates a `Companion.serializer()` for every
# @Serializable class; R8 cannot see those uses reflectively.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

-keep,includedescriptorclasses class com.pelonot.**$$serializer { *; }
-keepclassmembers class com.pelonot.** {
    *** Companion;
}
-keepclasseswithmembers class com.pelonot.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor / Supabase -----------------------------------------------------------
-dontwarn org.slf4j.**
-dontwarn io.ktor.**
-keep class io.ktor.client.engine.android.** { *; }

# Room ----------------------------------------------------------------------
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
