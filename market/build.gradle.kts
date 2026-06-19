import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

// :market — PRD-04 market-data data layer (recon RECON-data-layer.md): unified MarketDataApi over
// CoinGecko (per-contract OHLC/spot/history, via the key-proxy) + Binance (keyless klines). Web-capable
// (incl. js/wasm) like :rpc/:portfolio — depends only on :evm (no secp256k1). Ktor engine per target.
//
// ADDITIVE scaffold (PO greenlight 2026-06-19): the PriceSource relocation (:portfolio → here) is HELD
// for the official 04-kickoff (touches merged PRD-03) — this module is standalone and does NOT touch
// :portfolio. Caching/failover/server-indicators are the full PRD-08 service's job (this is the MVP bridge).
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
        namespace = "com.tneff.cyppie.market"
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
            api(projects.evm) // EvmAddress for ERC-20 asset refs; web-safe (no secp256k1)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinxJson)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.coroutines.core)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }

        androidMain.dependencies { implementation(libs.ktor.client.okhttp) }
        jvmMain.dependencies { implementation(libs.ktor.client.cio) }
        iosMain.dependencies { implementation(libs.ktor.client.darwin) }

        // js + wasmJs share the Js engine + the actual httpClient() factory via the default-hierarchy webMain.
        val webMain by getting { dependencies { implementation(libs.ktor.client.js) } }
    }
}
