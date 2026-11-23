pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // RootEncoder dipublikasikan lewat JitPack
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "kirim-layar"
include(":app")
