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
-keep class com.tom_roush.** { *; }
-dontwarn com.tom_roush.**

# PDFBox'ın şifreleme yolu (StandardSecurityHandler) BouncyCastleProvider'ı isimle (Class.forName)
# runtime'da çözer. -dontwarn yalnızca derleme uyarısını susturur, R8'in sınıfları STRIP ETMESİNİ
# ENGELLEMEZ — bu yüzden yalnızca -dontwarn ile PDF Şifreleme aracı debug'da çalışıp minify'li
# release'te ClassNotFoundException ile çöker. -keep şart.
-keep class org.bouncycastle.** { *; }
-keepnames class org.bouncycastle.jce.provider.BouncyCastleProvider
-dontwarn org.bouncycastle.**
-dontwarn org.apache.harmony.**
-dontwarn javax.naming.**
-dontwarn java.awt.**

# Play Billing / Ads consumer rules are bundled with the AARs; nothing extra required here.
