rootProject.name = "Cyppie"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        // Reown/WalletConnect Android SDK (ADR-0015) transitives — WalletConnect's Scarlet fork +
        // java-multibase are JitPack-hosted (Reown's documented requirement), scoped to those groups.
        maven("https://jitpack.io") {
            mavenContent {
                includeGroupByRegex("com\\.github\\..*") // JitPack namespace (multiformats, komputing.kethereum, …)
                includeGroupAndSubgroups("com.walletconnect") // WalletConnect's Scarlet fork
            }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(":app:androidApp")
include(":app:desktopApp")
include(":app:shared")
include(":app:webApp")
include(":core")
include(":designsystem")
include(":feature:auth")
include(":feature:onboarding")
include(":feature:wallet")
include(":feature:portfolio")
include(":server")
include(":evm")
include(":wallet")
include(":rpc")
include(":storage")
include(":walletcore")
include(":send")
include(":portfolio")
include(":walletconnect")
