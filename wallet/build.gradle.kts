import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
}

// :wallet — L1 Key/Account layer (KAN-56) + L2 EVM-tx stack (KAN-58). secp256k1-only; builds on
// the web-safe :evm primitives. NO js/wasm: secp256k1-kmp has no JS/Wasm binding (ADR-0008/0016,
// PRD-02 §2 "Web read-only"). Web read-only (RPC reads, NFT) uses :evm + :rpc instead.
kotlin {
    // EvmCrypto is an expect/actual object (still Beta in Kotlin) — opt in to silence the warning.
    compilerOptions {
        freeCompilerArgs.add("-Xexpect-actual-classes")
    }

    iosArm64()
    iosSimulatorArm64()

    jvm()

    androidLibrary {
        namespace = "com.tneff.cyppie.wallet"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        withHostTest {}
    }

    // Default hierarchy gives us iosMain over iosArm64 + iosSimulatorArm64.
    applyDefaultHierarchyTemplate()

    sourceSets {
        // commonMain stays free of the native crypto: bitcoin-kmp/secp256k1 (no Android-AAR variant)
        // live only in the platform source sets, reached via the internal `EvmCrypto` expect object —
        // keeps commonMain compilable for every target incl. Android. Web-safe bits come from :evm.
        commonMain.dependencies {
            api(projects.evm) // web-safe primitives: EvmAddress, Quantity, Rlp, Erc20Abi, Keccak
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }

        // JVM + Android share one actual implementation (both consume the plain -jvm jar).
        val jvmAndroidMain by creating {
            dependsOn(commonMain.get())
            dependencies {
                implementation(libs.bitcoin.kmp.jvm)
                implementation(libs.cryptography.random.jvm) // CSPRNG (BIP-39 entropy)
            }
        }
        jvmMain.get().dependsOn(jvmAndroidMain)
        androidMain.get().dependsOn(jvmAndroidMain)

        jvmMain.dependencies {
            implementation(libs.secp256k1.kmp.jni.jvm)
        }
        androidMain.dependencies {
            implementation(libs.secp256k1.kmp.jni.android)
        }

        // iOS: multiplatform artifacts resolve to the native klibs (secp256k1 bundled transitively).
        iosMain.dependencies {
            implementation(libs.bitcoin.kmp)
            implementation(libs.cryptography.random) // CSPRNG (BIP-39 entropy)
        }
    }
}

// KAN-117 — Gate-Hygiene: `secp256k1-kmp-jni-android` ships device-ABI `.so`s and cannot load on the
// Robolectric host JVM (`UnsatisfiedLinkError` via `NativeSecp256k1AndroidLoader`). These signing/
// derivation vectors run green under `:wallet:jvmTest` (`secp256k1-kmp-jni-jvm`), so the crypto gate is
// jvmTest — exclude exactly the JNI-bound classes from the host-android unit test to keep it green +
// meaningful. Non-JNI commonTests (e.g. MnemonicTest) still run on androidHostTest.
tasks.withType<Test>().configureEach {
    if (name == "testAndroidHostTest") {
        filter {
            excludeTestsMatching("com.tneff.cyppie.wallet.Bip44VectorsTest")
            excludeTestsMatching("com.tneff.cyppie.wallet.EvmKeyManagerTest")
            excludeTestsMatching("com.tneff.cyppie.wallet.tx.*") // Eip1559Vectors/External + EvmTransactionSigner
            isFailOnNoMatchingTests = false
        }
    }
}
