plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.retrobit.bitkeyboard"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.retrobit.bitkeyboard"
        minSdk = 21
        targetSdk = 35
        versionCode = 2
        versionName = "1.1.0"
        resourceConfigurations += listOf("id", "en")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += setOf("META-INF/*.kotlin_module")
    }
}

dependencies {
    // Sengaja tanpa dependensi eksternal (tanpa AndroidX, tanpa library iklan).
}
