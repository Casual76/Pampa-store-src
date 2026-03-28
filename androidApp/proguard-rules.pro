# Proguard rules for Pampa Store
-keepattributes *Annotation*
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}
-keep class com.pampa.store.data.model.** { *; }
-keep class com.pampa.store.data.api.** { *; }
-keep class com.pampa.store.data.local.** { *; }
-dontwarn org.slf4j.**
