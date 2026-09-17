import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// AnalyticsService.swift'teki "GoogleService-Info.plist bundle'da yoksa FirebaseApp.configure()
// çağrılmaz" davranışının Android karşılığı: google-services plugin'i `google-services.json`
// yoksa build'i (defaultConfig'e google_app_id/google_api_key string kaynağı üretemediği için)
// patlatır — bu yüzden yalnızca dosya gerçekten varsa uygulanır. Dependency (aşağıda) koşulsuzdur;
// FirebaseApp.getApps() boş kalır ve analytics/Analytics.kt no-op'a düşer.
val hasFirebaseConfig = project.file("google-services.json").exists()
if (hasFirebaseConfig) {
    apply(plugin = "com.google.gms.google-services")
}

// Yayın imzalama bilgileri gizli tutulur: `android/keystore.properties` (.gitignore'da) varsa
// oradan okunur; yoksa release derlemesi (CI/lokal test için) debug imzasına düşer — gerçek Play
// Store yüklemesi öncesi bu dosya `keytool` ile üretilmiş gerçek bir keystore'a işaret etmeli.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}
val hasReleaseKeystore = keystorePropertiesFile.exists()

val admobAppId = keystoreProperties.getProperty("admobAppId")
    ?: System.getenv("ADMOB_APP_ID")
    ?: "ca-app-pub-3940256099942544~3347511713"

val admobBannerId = keystoreProperties.getProperty("admobBannerId")
    ?: System.getenv("ADMOB_BANNER_ID")
    ?: "ca-app-pub-3940256099942544/9214589741"

val admobInterstitialId = keystoreProperties.getProperty("admobInterstitialId")
    ?: System.getenv("ADMOB_INTERSTITIAL_ID")
    ?: "ca-app-pub-3940256099942544/1033173712"

val admobRewardedInterstitialId = keystoreProperties.getProperty("admobRewardedInterstitialId")
    ?: System.getenv("ADMOB_REWARDED_INTERSTITIAL_ID")
    ?: "ca-app-pub-3940256099942544/5354046379"

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
        vectorDrawables.useSupportLibrary = true
        resourceConfigurations += setOf("tr")

        manifestPlaceholders["admobAppId"] = admobAppId
        buildConfigField("boolean", "HAS_FIREBASE", hasFirebaseConfig.toString())
        buildConfigField("String", "ADMOB_BANNER_ID", "\"$admobBannerId\"")
        buildConfigField("String", "ADMOB_INTERSTITIAL_ID", "\"$admobInterstitialId\"")
        buildConfigField("String", "ADMOB_REWARDED_INTERSTITIAL_ID", "\"$admobRewardedInterstitialId\"")

        // Uygulama tamamen Türkçe; gereksiz dil kaynaklarının (kütüphanelerden gelen) APK/AAB'ye
        // dahil edilmesini önler.
    }

    signingConfigs {
        getByName("debug") {
            storeFile = file("${rootDir}/debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
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
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseKeystore) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
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
        buildConfig = true // BuildConfig.DEBUG ile AdsManager'da debug/prod reklam ID ayrımı için
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    bundle {
        language {
            enableSplit = false
        }
        density {
            enableSplit = true
        }
        abi {
            enableSplit = true
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    implementation(libs.core.ktx)
    implementation(libs.core.splashscreen)
    implementation(libs.activity.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.extended)

    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.process)

    implementation(libs.navigation.compose)

    implementation(libs.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.play.services.ads)
    implementation(libs.user.messaging.platform)
    implementation(libs.billing.ktx)
    implementation(libs.pdfbox.android)

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)

    implementation(libs.mlkit.text.recognition)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.play.review.ktx)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.compose.ui.test.junit4)
}
