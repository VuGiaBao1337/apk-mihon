plugins {
    id("com.android.application") version "8.2.2"
    id("org.jetbrains.kotlin.android") version "1.9.22"
}

android {
    namespace = "eu.kanade.tachiyomi.extension.vi.moetruyen"
    compileSdk = 34

    defaultConfig {
        applicationId = "eu.kanade.tachiyomi.extension.vi.moetruyen"
        minSdk = 21
        targetSdk = 34
        versionCode = 2
        versionName = "1.5.2"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
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
}

dependencies {
    // Local stub module — provides Mihon API classes for compilation only.
    // At runtime, Mihon supplies the real implementations.
    compileOnly(project(":stub"))

    // Also needed at compile time; Mihon provides them at runtime
    compileOnly("org.jsoup:jsoup:1.17.2")
    compileOnly("com.squareup.okhttp3:okhttp:4.12.0")
    compileOnly("io.reactivex:rxjava:1.3.8")
}
