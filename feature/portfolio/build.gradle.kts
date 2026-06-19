import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// :feature:portfolio — Compose-MP Portfolio UI (PF-1 Overview now; detail/history later). Unlike
// :feature:wallet this is **web-capable** (js/wasmJs) because PRD-03 read-only portfolio must render
// on Web — so it depends only on the web-capable :portfolio (logic) + :designsystem, never on the
// non-web :walletcore. DI/nav are wired by the app shell (the screen takes a ViewModel + callbacks),
// which keeps this module free of Koin/Navigation and therefore web-safe.
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
        namespace = "com.tneff.cyppie.feature.portfolio"
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
        }
        commonMain.dependencies {
            api(projects.designsystem)
            api(projects.portfolio)

            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            // Adaptive layouts / Window Size Classes (ADR-0012) — web 2-column at Medium/Expanded.
            implementation(libs.compose.material3.adaptive)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }
        // Compose UI tests live only on JVM targets — runComposeUiTest API + Skiko native runtime (ADR-0011).
        getByName("jvmTest").dependencies {
            implementation(libs.compose.uiTest)
            implementation(compose.desktop.currentOs)
        }
        // Android host (JVM) Compose UI & config-change tests via Robolectric (ADR-0013).
        getByName("androidHostTest").dependencies {
            implementation(libs.compose.uiTest)
            implementation(libs.robolectric)
            implementation(libs.androidx.test.core)
            implementation(libs.androidx.activity.compose)
        }
    }
}

// i18n (ADR-0004 / SPEC §5.5): generate a stable, public Res class for the portfolio strings
// (composeResources/values*/strings.xml — pf_* keys; en base + de now, full 14-locale via KAN-109).
compose.resources {
    publicResClass = true
    packageOfResClass = "com.tneff.cyppie.feature.portfolio.generated.resources"
    generateResClass = always
}
