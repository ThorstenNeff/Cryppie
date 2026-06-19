import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

// :walletconnect-e2e — KAN-127 deterministic WalletConnect test double (FakeWalletConnectController +
// WcE2eScript + WcE2e), split OUT of :walletconnect so it is NEVER compiled into a release build.
//
// 🔒 Prod-separation (security review, ADR-0015/0005): the app depends on this ONLY via
// `debugImplementation(projects.walletconnectE2e)` (Android) / debug-framework-only (iOS) — so the fake
// transport is physically absent from the release binary. The KMP `androidLibrary` plugin has no
// debug/release build-types (single `android` variant), so a `src/androidDebug` source set could not
// achieve this within :walletconnect — hence a separate, debug-only module. `WcE2e.fakeOrNull` adds a
// runtime `isDebugBuild` belt; the deep-link activation vector is registered debug-only by the app (L3).
kotlin {
    iosArm64()
    iosSimulatorArm64()

    jvm()

    androidLibrary {
        namespace = "com.tneff.cyppie.walletconnect.e2e"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        withHostTest {}
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            api(projects.walletconnect) // WcTransport / WcEvent / WcSessionRequest + CAIP helpers (+ :evm via :wallet)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json) // parse the WcE2eScript JSON (JsonElement; no plugin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
