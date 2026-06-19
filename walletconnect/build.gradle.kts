import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

// :walletconnect — L4 WalletConnect v2 transport (KAN-62, ADR-0015). Non-Compose transport layer
// (the approval sheets are a separate UI story, KAN-50). NON-WEB: signs via :wallet (secp256k1), and
// WC has no web SDK anyway. Android = Reown WalletKit, iOS = reown-swift (Swift shim), Desktop = no-op.
kotlin {
    iosArm64()
    iosSimulatorArm64()

    jvm()

    androidLibrary {
        namespace = "com.tneff.cyppie.walletconnect"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        withHostTest {}
    }

    // EvmCrypto-style expect/actual; opt in to the still-Beta expect/actual class warning.
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            api(projects.wallet) // EvmKeyManager / tx signer / EvmAddress
            // KAN-62 WC-Send adapter: eth_sendTransaction → the one KAN-91 SendOrchestrator (SendInput).
            api(projects.send)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json) // parse eth_signTypedData_v4 JSON (JsonElement; no plugin)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        // Chunk 3: Android Reown WalletKit (Android-only; iOS reown-swift shim = Chunk 4).
        androidMain.dependencies {
            implementation(project.dependencies.platform(libs.reown.android.bom))
            implementation(libs.reown.android.core)
            implementation(libs.reown.walletkit)
        }
    }
}

// KAN-117-style gate hygiene: `secp256k1-kmp-jni-android` ships device-ABI `.so`s and cannot load on the
// Robolectric host JVM (`UnsatisfiedLinkError` via `NativeSecp256k1AndroidLoader`). The signing/recover
// vectors run green under `:walletconnect:jvmTest` (`secp256k1-kmp-jni-jvm`), so the crypto gate is
// jvmTest — exclude exactly the JNI-bound tests from the host-android unit test to keep it green +
// meaningful. The non-JNI tests (request decode, disclosure, WC-Send adapter, EIP-712 digest/validation)
// still run on androidHostTest.
tasks.withType<Test>().configureEach {
    if (name == "testAndroidHostTest") {
        filter {
            excludeTestsMatching("com.tneff.cyppie.walletconnect.WalletConnectSignerTest")
            excludeTestsMatching("com.tneff.cyppie.walletconnect.Eip712Test.signTypedDataV4RecoversToSigner")
            isFailOnNoMatchingTests = false
        }
    }
}
