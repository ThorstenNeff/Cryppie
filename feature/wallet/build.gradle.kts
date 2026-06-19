import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// :feature:wallet — Compose-MP wallet feature screens (Receive now; Home/Send-UI/WalletConnect-UI
// later). Modeled on :feature:onboarding. Targets android/ios/jvm: depends on the non-web
// :walletcore (api(:wallet)); Web read-only is PRD-03 (M1 split). qrose renders the Receive QR.
kotlin {
    iosArm64()
    iosSimulatorArm64()

    jvm()

    androidLibrary {
        namespace = "com.tneff.cyppie.feature.wallet"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        androidResources {
            enable = true
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
            // Coil3 Ktor engine — Android (KAN-105). Not transitive from :rpc (implementation there).
            implementation(libs.ktor.client.okhttp)
            // Biometric Send re-auth (KAN-119) — BiometricPrompt + CryptoObject, needs a FragmentActivity host.
            implementation(libs.androidx.biometric)
            implementation(libs.androidx.fragment)
        }
        jvmMain.dependencies {
            // Coil3 Ktor engine — Desktop/JVM (KAN-105).
            implementation(libs.ktor.client.cio)
        }
        iosMain.dependencies {
            // Coil3 Ktor engine — iOS (KAN-105).
            implementation(libs.ktor.client.darwin)
        }
        commonMain.dependencies {
            api(projects.designsystem)
            api(projects.walletcore)
            // SeedSession — the unlocked seed source the wallet shell builds the repository from (KAN-103).
            implementation(projects.storage)
            // Send orchestrator (KAN-91/ADR-0019) — the Send UI (KAN-110) renders prepare→disclose→sign.
            implementation(projects.send)
            // WalletConnect layer (KAN-62) — the WC-UI (KAN-126) consumes the controller + WcSendAdapter.
            implementation(projects.walletconnect)
            // Portfolio (KAN-114): the PF-1 screen/VM + the assembler (PortfolioService/AlchemyPriceSource)
            // the wallet shell wires to the live proxy clients.
            implementation(projects.feature.portfolio)
            implementation(projects.portfolio)

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            // DI — Koin (ADR-0007)
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            // Navigation 3 (ADR-0006)
            implementation(libs.navigation3.ui)

            // Adaptive layouts / Window Size Classes (ADR-0012)
            implementation(libs.compose.material3.adaptive)

            // QR rendering (KAN-78) — qrose, Compose-MP vector QR
            implementation(libs.qrose)

            // NFT media (KAN-105) — Coil3 AsyncImage + Ktor3 network fetcher (Alchemy-cached URLs only).
            implementation(libs.coil.compose)
            implementation(libs.coil.network.ktor3)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        getByName("jvmTest").dependencies {
            implementation(libs.compose.uiTest)
            implementation(compose.desktop.currentOs)
            // Deterministic viewModelScope: drive Dispatchers.Main from a test dispatcher (KAN-105 NFT VM).
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.compose.uiTest)
            implementation(libs.robolectric)
            implementation(libs.androidx.test.core)
            implementation(libs.androidx.activity.compose)
        }
    }
}

// i18n (ADR-0004 / SPEC §5.5): generate a stable, public Res class for the wallet strings
// (composeResources/values*/strings.xml imported from ../Cryptasa/i18n — wallet_*/receive_* keys).
compose.resources {
    publicResClass = true
    packageOfResClass = "com.tneff.cyppie.feature.wallet.generated.resources"
    generateResClass = always
}
