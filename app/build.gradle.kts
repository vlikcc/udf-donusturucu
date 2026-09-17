import java.util.Properties
import java.io.FileInputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// 1. keystore.properties dosyasını oku (varsa)
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

// AdMob Gerçek ve Test ID Tanımları
val PROD_ADMOB_APP_ID = "ca-app-pub-1041738122428212~5886914438"
val PROD_ADMOB_BANNER_ID = "ca-app-pub-1041738122428212/3416612776"
val PROD_ADMOB_INTERSTITIAL_ID = "ca-app-pub-1041738122428212/4329554409"
val PROD_ADMOB_REWARDED_INTERSTITIAL_ID = "ca-app-pub-1041738122428212/2380645547"

val TEST_ADMOB_APP_ID = "ca-app-pub-3940256099942544~3347511713"

// Öncelik sırası: keystore.properties > Ortam Değişkenleri (CI/CD) > Sabit Gerçek ID'ler
val effectiveAdmobAppId = keystoreProperties.getProperty("admobAppId")
    ?: System.getenv("ADMOB_APP_ID")
    ?: PROD_ADMOB_APP_ID

val effectiveBannerId = keystoreProperties.getProperty("admobBannerId")
    ?: System.getenv("ADMOB_BANNER_ID")
    ?: PROD_ADMOB_BANNER_ID

val effectiveInterstitialId = keystoreProperties.getProperty("admobInterstitialId")
    ?: System.getenv("ADMOB_INTERSTITIAL_ID")
    ?: PROD_ADMOB_INTERSTITIAL_ID

val effectiveRewardedInterstitialId = keystoreProperties.getProperty("admobRewardedInterstitialId")
    ?: System.getenv("ADMOB_REWARDED_INTERSTITIAL_ID")
    ?: PROD_ADMOB_REWARDED_INTERSTITIAL_ID

val forceRealAds = (keystoreProperties.getProperty("forceRealAds")
    ?: System.getenv("FORCE_REAL_ADS")
    ?: "false").toBoolean()

val hasReleaseKeystore = keystoreProperties.containsKey("storeFile") &&
        file(keystoreProperties.getProperty("storeFile")).exists()

android {
    namespace = "com.velikececi.udfdonusturucu"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.velikececi.udfdonusturucu"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "PROD_BANNER_ID", "\"$effectiveBannerId\"")
        buildConfigField("String", "PROD_INTERSTITIAL_ID", "\"$effectiveInterstitialId\"")
        buildConfigField("String", "PROD_REWARDED_INTERSTITIAL_ID", "\"$effectiveRewardedInterstitialId\"")
        buildConfigField("boolean", "FORCE_REAL_ADS", "$forceRealAds")
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            val debugAppId = if (forceRealAds) effectiveAdmobAppId else TEST_ADMOB_APP_ID
            manifestPlaceholders["admobAppId"] = debugAppId
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            manifestPlaceholders["admobAppId"] = effectiveAdmobAppId
            signingConfig = if (hasReleaseKeystore) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // DataStore Preferences
    implementation(libs.androidx.datastore.preferences)

    // Google AdMob & UMP
    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform)

    // Google Play Billing
    implementation(libs.billing.ktx)

    // PDFBox Android
    implementation(libs.pdfbox.android)

    // Apache POI (OOXML for docx)
    implementation(libs.poi.ooxml)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
