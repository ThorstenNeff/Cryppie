import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// :feature:copy — Copy-Trading "Follow-Trader" UX (KAN-155): pick a trader → set the budget/cap → no-blind
// disclosure of the VERIFIED grant (the Copy-Service may trade up to cap X on router Y on your behalf) →
// re-auth → on-device sign (Dev-2's copy grant/crypto, KAN-154) → activation. **Native-only**
// (ios/jvm/android) — signing rides `:aa`→`:wallet` (secp256k1), not web. DI/nav from the app shell.
kotlin {
    iosArm64()
    iosSimulatorArm64()

    jvm()

    androidLibrary {
        namespace = "com.tneff.cyppie.feature.copy"
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

// i18n (ADR-0004 / KAN-155): public Res class for the Copy strings (copy_* — synced from ../Cryptasa/i18n).
compose.resources {
    publicResClass = true
    packageOfResClass = "com.tneff.cyppie.feature.copy.generated.resources"
    generateResClass = always
}
