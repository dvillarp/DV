import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Clave de Google Maps: local.properties (MAPS_API_KEY=...) o variable de entorno MAPS_API_KEY
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val mapsKey: String = localProps.getProperty("MAPS_API_KEY") ?: System.getenv("MAPS_API_KEY") ?: ""

android {
    namespace = "com.dv.wimc"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dv.wimc"
        minSdk = 26
        targetSdk = 35
        versionCode = 42
        versionName = "4.2 Final DV"
        manifestPlaceholders["MAPS_API_KEY"] = mapsKey
    }

    // Firma fija (misma clave en todas las versiones => el APK nuevo se instala encima del anterior).
    signingConfigs {
        create("dv") {
            storeFile = file("dv-wimc.jks")
            storePassword = "dvwimc123"
            keyAlias = "dv"
            keyPassword = "dvwimc123"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("dv")
        }
        debug {
            signingConfig = signingConfigs.getByName("dv")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.fragment:fragment:1.8.5")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")

    implementation("com.google.maps.android:maps-compose:4.3.3")
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.0.1")
}
