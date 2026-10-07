pluginManagement {
    repositories {
        maven { url = uri(System.getProperty("user.home") + "/.gradle/eagle-maven") }
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
    }
}

rootProject.name = "EAGLE-VPN"
include(":app")
