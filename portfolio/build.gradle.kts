import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

// :portfolio — read-only portfolio logic (PRD-03 / ADR-0017). The first fully **web-capable** feature
// (FR-7): depends on :rpc + :evm only (no :wallet/secp256k1, no key derivation) and takes account
// addresses as input, so it builds on android/ios/jvm AND js/wasm. Valuation via a migratable
// `PriceSource`; rich metrics (P&L/Sharpe/Drawdown) carry an FR-9 robust/≈ confidence flag.
kotlin {
    iosArm64()
    iosSimulatorArm64()

    jvm()

    js {
        browser()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    androidLibrary {
        namespace = "com.tneff.cyppie.portfolio"
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
            api(projects.rpc) // L3 abstraction + the upcoming Data/Prices REST clients (ADR-0010 update)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
