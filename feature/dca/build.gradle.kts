import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// :feature:dca — DCA-Bot UI (PRD-05 Ph1): the Smart-Session grant UX (no-blind disclosure + enable), the
// pending-DCA sign prompt (re-auth → AaSigner), and the schedule UI (create / running sessions /
// revoke + kill-switch). **Native-only** (ios/jvm/android) — signing rides `:aa`→`:wallet` (secp256k1),
// which is not web; DCA is an on-device-custody feature. DI/nav from the app shell (takes VMs + callbacks).
kotlin {
    iosArm64()
    iosSimulatorArm64()

    jvm()

    androidLibrary {
        namespace = "com.tneff.cyppie.feature.dca"
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

// i18n (ADR-0004 / KAN-138): public Res class for the DCA strings (dca_* — synced from ../Cryptasa/i18n).
compose.resources {
    publicResClass = true
    packageOfResClass = "com.tneff.cyppie.feature.dca.generated.resources"
    generateResClass = always
}
