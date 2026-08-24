-keepattributes *Annotation*
-keep class com.pampa.store.data.**$$serializer { *; }
-keepclassmembers class com.pampa.store.data.** {
    *** Companion;
}
-dontwarn org.slf4j.**
