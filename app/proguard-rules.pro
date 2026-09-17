# Apache POI
-dontwarn org.apache.poi.**
-keep class org.apache.poi.** { *; }
-dontwarn org.apache.xmlbeans.**
-keep class org.apache.xmlbeans.** { *; }
-dontwarn javax.xml.stream.**
-keep class javax.xml.stream.** { *; }

# PDFBox Android
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Play Services Ads & UMP
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.android.ump.** { *; }
