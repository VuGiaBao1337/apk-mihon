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
        versionCode = 1
        versionName = "1.5.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
        debug {
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
    // Official Mihon extension stub library — provides all base classes for compilation.
    // At runtime, Mihon itself provides these; we only need them to compile.
    compileOnly("com.github.mihonapp:extensions-lib:1.5")

    // Provided by Mihon at runtime; needed at compile-time only
    compileOnly("org.jsoup:jsoup:1.17.2")
    compileOnly("com.squareup.okhttp3:okhttp:4.12.0")
    compileOnly("io.reactivex:rxjava:1.3.8")
}
