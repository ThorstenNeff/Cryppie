rootProject.name = "Cyppie"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

// KAN-123: reown 1.6.14 requires AGP 9.1 + compileSdk 37 (Android 17). The SDK installs that API as the
// minor-versioned base platform `android-37.0`, but AGP's `compileSdk = 37` looks for a literal `android-37`
// (the KMP `androidLibrary` DSL has no `compileSdkMinor`). Self-heal idempotently per machine — symlink
// `platforms/android-37 -> platforms/android-37.0` — so a fresh clone + CI build with no manual setup. Runs
// before any Android plugin resolves the platform. Safe no-op when the link already exists or the SDK is absent.
run {
    val sdkDir = java.io.File(settingsDir, "local.properties").takeIf { it.exists() }
        ?.readLines()?.firstOrNull { it.startsWith("sdk.dir=") }?.substringAfter("sdk.dir=")?.trim()
        ?: System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
    if (sdkDir != null) {
        val platforms = java.io.File(sdkDir, "platforms")
        val base = java.io.File(platforms, "android-37.0")
        val link = java.io.File(platforms, "android-37")
        if (base.isDirectory && !link.exists()) {
            runCatching { java.nio.file.Files.createSymbolicLink(link.toPath(), base.toPath()) }
                .onSuccess { println("[KAN-123] linked platforms/android-37 -> android-37.0 (compileSdk 37 self-heal)") }
                .onFailure { println("[KAN-123] WARN could not link android-37 -> android-37.0: ${it.message}") }
        }
    }
}

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
