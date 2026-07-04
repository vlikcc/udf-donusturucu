# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.velikececi.udfdonusturucu.**$$serializer { *; }
-keepclassmembers class com.velikececi.udfdonusturucu.** {
    *** Companion;
}
-keepclasseswithmembers class com.velikececi.udfdonusturucu.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# PDFBox-Android
-dontwarn org.bouncycastle.**
-dontwarn org.apache.harmony.**
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**

# Play Billing / Ads consumer rules are bundled with the AARs; nothing extra required here.
