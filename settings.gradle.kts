pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "org.jetbrains.kotlin.plugin.compose") {
                useModule("org.jetbrains.kotlin:compose-compiler-gradle-plugin:${requested.version}")
            }
        }
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "com.android.application") useModule("com.android.tools.build:gradle:9.3.1")
            if (requested.id.id == "com.android.library") useModule("com.android.tools.build:gradle:9.3.1")
        }
    }
    repositories {
        maven { url = uri("file:///data/data/com.termux/files/home/agp-local-repo") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri(System.getProperty("user.home") + "/agp-local-repo") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        google()
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://jitpack.io") }
        maven { url = uri(System.getProperty("user.home") + "/agp-local-repo") }
        maven {
            url = uri("https://central.sonatype.com/repository/maven-snapshots/")
            mavenContent { snapshotsOnly() }
            content { includeGroup("io.github.sagernet") }
        }
        maven { url = uri("https://api.xposed.info/") }
    }
}
rootProject.name = "sing-box"
include(":app")
include(":libxposed-api")
project(":libxposed-api").projectDir = file("third_party/libxposed-api")
