import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.kotlinSerialization)
}

// :auth — App-side SIWE authentication (PRD-05, KAN-141). After wallet-unlock: build an EIP-4361 SIWE
// message on-device, sign it personal_sign-style (one auditable EIP-191 path in :wallet), exchange it at
// Keycloak (Direct-Grant) for an RS256 JWT, persist it OS-backed (:storage SecureKeyStore), and surface a
// SessionTokenProvider bearer for the User-Service clients (DCA + all 04–07). 🔒 Key-path: only the SIWE
// digest in / the signature out — the seed never leaves :wallet. Non-web (signing).
kotlin {
    iosArm64()
    iosSimulatorArm64()

    jvm()

    androidLibrary {
        namespace = "com.tneff.cyppie.auth"
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
            api(projects.evm) // EvmAddress (EIP-55) for the SIWE address
            api(projects.wallet) // the EIP-191 personal-sign primitive (SiweSigner) + SeedSource
            implementation(projects.storage) // SecureKeyStore — OS-backed JWT persistence
            implementation(projects.rpc) // shared HTTP plumbing / base-url config
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
