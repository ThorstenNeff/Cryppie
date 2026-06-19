import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

// :aa — Account-Abstraction core (PRD-05, Epic KAN-138). The on-device App side of the AA stack
// (Kernel-7579 + Smart Sessions + EIP-7702 + Pimlico): the session-config model (Ph0 §2), the
// User-Service DCA API client (the app talks ONLY to the JWT User-Service, never the loopback
// aa-trigger), and AaSigner — the sign orchestrator that signs a backend-built userOpHash on-device
// (re-auth → fresh SeedSource → EvmKeyManager.sign → serialize → zeroize, mirroring SendOrchestrator).
// 🔒 Key-path: only digests in / signatures out — the seed never leaves :wallet. Non-web (signing).
kotlin {
    iosArm64()
    iosSimulatorArm64()

    jvm()

    androidLibrary {
        namespace = "com.tneff.cyppie.aa"
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
            api(projects.wallet) // L2 signer (EvmKeyManager.sign) + SeedSource
            api(projects.rpc) // proxy base-url config; shared HTTP plumbing
            api(projects.walletcore) // AccountManager + EvmChain
            implementation(projects.storage) // SecureSeedSource (unlock)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinxJson)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}

// Gate-hygiene (mirrors :send): AaSigner signing pulls `secp256k1-kmp-jni-android` (via :wallet), which
// can't load on the Robolectric host JVM → the signer test runs under :aa:jvmTest, excluded from host-android.
tasks.withType<Test>().configureEach {
    if (name == "testAndroidHostTest") {
        filter {
            excludeTestsMatching("com.tneff.cyppie.aa.AaSignerTest")
            isFailOnNoMatchingTests = false
        }
    }
}
