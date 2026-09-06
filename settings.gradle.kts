@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        exclusiveContent {
            forRepository { google() }
            filter {
                includeGroup("androidx.baselineprofile")
                includeGroup("androidx.benchmark")
                includeGroup("androidx.databinding")
                includeGroupAndSubgroups("androidx.navigation")
                includeGroup("com.google.testing.platform")
                includeGroupAndSubgroups("com.android")
            }
        }
        exclusiveContent {
            forRepository { gradlePluginPortal() }
            filter {
                includeGroup("org.gradle.toolchains")
                includeModule(
                    "org.gradle.toolchains.foojay-resolver-convention",
                    "org.gradle.toolchains.foojay-resolver-convention.gradle.plugin",
                )
            }
        }
        mavenCentral { mavenContent { releasesOnly() } }
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google {
            content {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroup("com.google.android.gms")
                includeModule("com.google.android.material", "material")
                includeGroup("com.google.testing.platform")
            }
        }
        mavenCentral { mavenContent { releasesOnly() } }
    }
}

rootProject.name = "sms-forwarder-android"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
