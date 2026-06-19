import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

// :feature:market — Compose-MP Market UI (PRD-04). Web-capable (js/wasmJs), like :feature:portfolio:
// the market-data layer (Dev-2's :market, ADR-0025) is web-capable, so the charts render on Web too.
// The chart renderer is an expect/actual web-seam (MarketChart) so Web can swap a JS/Canvas renderer in
// later without touching callers. DI/nav are wired by the app shell (screen takes a ViewModel +
// callbacks) → no Koin/Navigation here → web-safe. Depends only on :designsystem now; :market is wired
// after its merge + the ADR-0025 PriceSource relocation (this module is scaffold/prep until then).
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
        namespace = "com.tneff.cyppie.feature.market"
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
            // api(projects.market) — wired after Dev-2's :market merge (ADR-0025).

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
        getByName("jvmTest").dependencies {
            implementation(libs.compose.uiTest)
            implementation(compose.desktop.currentOs)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.compose.uiTest)
            implementation(libs.robolectric)
            implementation(libs.androidx.test.core)
            implementation(libs.androidx.activity.compose)
        }
    }
}

// i18n (ADR-0004): the compose.resources block + the mkt_* keys (SoT/syncI18n) are added when the
// screen copy is finalized — none during scaffold (the screen uses no stringResource yet).
