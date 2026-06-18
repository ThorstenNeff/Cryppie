import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

// :storage — secure seed-at-rest (KAN-75, ADR-0009). PBKDF2 → KEK + AES-GCM over the BIP-39 seed,
// with a platform Keystore/Keychain seam for biometric convenience. NON-WEB (no js/wasm: no
// wallet-at-rest on web, ADR-0009/0016). Depends on :wallet for the L1 SeedSource.
kotlin {
    iosArm64()
    iosSimulatorArm64()

    jvm()

    androidLibrary {
        namespace = "com.tneff.cyppie.storage"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            api(projects.wallet) // SeedSource (L1)
            implementation(libs.cryptography.core)
            implementation(libs.cryptography.provider.optimal) // JDK on jvm/android, Apple on iOS
            implementation(libs.cryptography.random)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        // Android host (JVM) tests via Robolectric (ADR-0013) — exercises AndroidSecureKeyStore's
        // wrap/persist/unwrap round-trip with a fake-HW Cipher + Robolectric SharedPreferences (KAN-80).
        getByName("androidHostTest").dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.robolectric)
            implementation(libs.androidx.test.core)
        }
    }
}
