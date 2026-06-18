import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

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
       namespace = "com.tneff.cyppie.feature.onboarding"
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
            // L1 wallet (BIP-39 Mnemonic) + secure storage — only where they exist (no js/wasm; ADR-0008/0009).
            implementation(projects.wallet)
            implementation(projects.storage)
        }
        jvmMain.dependencies {
            implementation(projects.wallet)
            implementation(projects.storage)
        }
        iosMain.dependencies {
            implementation(projects.wallet)
            implementation(projects.storage)
        }
        commonMain.dependencies {
            api(projects.designsystem)

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

            // Navigation 3 (ADR-0006) — navigation3-ui brings navigation3-runtime transitively
            implementation(libs.navigation3.ui)

            // Adaptive layouts / Window Size Classes (ADR-0012)
            implementation(libs.compose.material3.adaptive)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        // Compose UI tests live only on JVM targets — runComposeUiTest API + Skiko runtime (ADR-0011).
        getByName("jvmTest").dependencies {
            implementation(libs.compose.uiTest)
            implementation(compose.desktop.currentOs)
        }
        // Android host (JVM) Compose UI tests via Robolectric (ADR-0013); verifies testTag visibility
        // (testTagsAsResourceId) on the Android renderer for KAN-10.
        getByName("androidHostTest").dependencies {
            implementation(libs.compose.uiTest)
            implementation(libs.robolectric)
            implementation(libs.androidx.test.core)
            implementation(libs.androidx.activity.compose)
        }
    }
}

// i18n (ADR-0004 / SPEC §5.5): generate a stable, public Res class for the onboarding strings
// (composeResources/values*/strings.xml imported from ../Cryptasa/i18n).
compose.resources {
    publicResClass = true
    packageOfResClass = "com.tneff.cyppie.feature.onboarding.generated.resources"
    generateResClass = always
}
