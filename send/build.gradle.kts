import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

// :send — the Send-flow orchestrator (KAN-91; ADR-0019 pending filing). The ONE orchestrator for both
// entry points (in-app send + WalletConnect `eth_sendTransaction`): resolve → complete (L3) → validate
// → disclose → sign (L2 over the unlocked seed) → broadcast (L3) → receipt. Enforces the TOCTOU
// ordering + signer/chain guardrails. Consumer of :rpc + :wallet + :storage + :walletcore (which do
// not depend on each other). Non-web (signing; Send is hidden on Web, FR-6). The WC adapter (decode →
// SendInput, respondSessionRequest) lands when :walletconnect (KAN-62) merges — kept out so this module
// stays buildable now.
kotlin {
    iosArm64()
    iosSimulatorArm64()

    jvm()

    androidLibrary {
        namespace = "com.tneff.cyppie.send"
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
            api(projects.evm)
            api(projects.wallet) // L2 signer + SeedSource
            api(projects.rpc) // L3 nonce/fees/estimate/broadcast/receipt
            api(projects.walletcore) // AccountManager + EvmChain
            implementation(projects.storage) // SecureSeedSource (unlock)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
