plugins {
    id("com.android.application") version "9.2.0"
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
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // We can use compileOnly because these are stubs provided by Mihon app at runtime.
    // Try to resolve from local maven first (published from tachiyomix-main), or fallback to JitPack.
    compileOnly("com.github.mihonapp:tachiyomix:1.6.0-SNAPSHOT") {
        isTransitive = false
    }

    // Dependencies needed for scraping
    implementation("org.jsoup:jsoup:1.15.4")
    implementation("com.squareup.okhttp3:okhttp:4.10.0")
    implementation("io.reactivex:rxandroid:1.2.1")
    implementation("io.reactivex:rxjava:1.3.8")
}
