pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "org.jetbrains.kotlin.android") {
                useVersion("1.9.22")
            }
        }
    }
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}


dependencyResolutionManagement {
    @Suppress("UnstableApiUsage")
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    @Suppress("UnstableApiUsage")
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MoeTruyenExtension"

include(":stub")
