plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val backendUrl = (project.findProperty("BASE_URL") as String?) ?: "https://chat.example.com"
val countryCode = (project.findProperty("DEFAULT_COUNTRY_CODE") as String?) ?: "62"

android {
    namespace = "com.chatku.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.chatku.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "BASE_URL", "\"$backendUrl\"")
        buildConfigField("String", "DEFAULT_COUNTRY_CODE", "\"$countryCode\"")
    }

    buildTypes {
        debug {
            manifestPlaceholders["usesCleartext"] = "true"
        }
        release {
            isMinifyEnabled = false
            manifestPlaceholders["usesCleartext"] = "false"
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Ditandatangani dengan debug key agar APK release bisa langsung di-install untuk uji coba.
            // Untuk Play Store, ganti dengan keystore Anda sendiri.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons.extended)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.retrofit)
    implementation(libs.retrofit.serialization)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.coil.compose)
    implementation(libs.socketio.client) {
        exclude(group = "org.json", module = "json")
    }
    testImplementation(libs.junit)
}
