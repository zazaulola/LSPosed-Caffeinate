plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.zazaulola.caffeinate"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.zazaulola.caffeinate"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        // Общий debug-ключ из репозитория, чтобы каждая сборка CI ставилась поверх
        // предыдущей. Для debug-сборок это нормально: ключ не секретный.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            // Xposed-модуль: не обфусцируем, чтобы LSPosed нашёл точку входа по имени класса.
            isMinifyEnabled = false
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
        abortOnError = false
    }
}

dependencies {
    compileOnly("de.robv.android.xposed:api:82")
}
