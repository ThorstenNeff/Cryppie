import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// :feature:strat — Smart-Strategies "target-allocation auto-rebalance" UX (KAN-166, PRD-07b / Vaults-B):
// set target weights (sum 100%) over an allowlist + budget → no-blind, SELL-SIDE disclosure of the VERIFIED
// grant (the strategy may sell up to cap X of your basket tokens on router Y) → re-auth → on-device sign
// (Dev-2's strategy grant/crypto, KAN-165) → activation. Mirrors :feature:copy (Copy-trust-pattern reuse).
// **Native-only** (ios/jvm/android) — signing rides `:aa`→`:wallet` (secp256k1), not web. DI/nav from the shell.
kotlin {
    iosArm64()
    iosSimulatorArm64()

    jvm()

    androidLibrary {
        namespace = "com.tneff.cyppie.feature.strat"
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

    applyDefaultHierarchyTemplate()

    sourceSets {
        androidMain.dependencies {
            implementation(libs.compose.uiToolingPreview)
        }
        commonMain.dependencies {
            api(projects.designsystem)
            api(projects.aa)

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("jvmTest").dependencies {
            implementation(libs.compose.uiTest)
            implementation(compose.desktop.currentOs)
        }
    }
}

// i18n (ADR-0004 / KAN-166): public Res class for the Strategy strings (strat_* — synced from ../Cryptasa/i18n).
compose.resources {
    publicResClass = true
    packageOfResClass = "com.tneff.cyppie.feature.strat.generated.resources"
    generateResClass = always
}
