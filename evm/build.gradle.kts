import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

// :evm — web-safe EVM primitives shared by :wallet (signing, non-web) and :rpc (networking, web).
// Full target set INCLUDING js/wasm: nothing here needs secp256k1; keccak comes from KotlinCrypto
// sha3 (multiplatform incl. js/wasm). This is what makes Web read-only viable (ADR-0016, FR-6).
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
        namespace = "com.tneff.cyppie.evm"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        withHostTest {}
    }

    sourceSets {
        commonMain.dependencies {
            implementation(libs.kotlincrypto.hash.sha3)
            // Runtime JSON only (parseToJsonElement) for the EIP-712 typed-data digest (KAN-143) — no
            // @Serializable / compiler plugin needed. Web-safe (same lib :rpc/:market use cross-target).
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}
