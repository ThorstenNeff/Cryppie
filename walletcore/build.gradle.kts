import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

// :walletcore — read-side wallet domain (accounts/addresses/balances) orchestrating :wallet (L1) +
// :rpc (L3). Non-web (depends on :wallet/secp256k1). Write/send/seed/WC paths fold in after the
// KAN-75 / KAN-62 merges. Skeleton — reconciled with the Wallet-Core slice plan when it lands.
//
// M1 (architecture note): `api(:wallet)` makes this module non-web. Only `AccountManager` (key
// derivation) truly needs `:wallet`; the `WalletRepository` read/balance paths are address-based and
// need only `EvmAddress` (`:evm`) + `:rpc` — both web-capable (ADR-0016). Keeping those reads
// `:wallet`-free makes a later web-read-only split trivial; the real split lands when Web read-only
// balances ship (open web address-source question, PRD-03 / web strategy). Non-blocking for now.
kotlin {
    iosArm64()
    iosSimulatorArm64()

    jvm()

    androidLibrary {
        namespace = "com.tneff.cyppie.walletcore"
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
            api(projects.wallet) // EvmKeyManager / EvmAccount / SeedSource
            api(projects.rpc) // EvmRpcClient (read paths)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
