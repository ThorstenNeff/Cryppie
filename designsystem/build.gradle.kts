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
       namespace = "com.tneff.cyppie.designsystem"
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
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
        }
        commonTest.dependencies {
            // commonTest holds only pure logic tests (WCAG math) — no Compose UI test artifact here,
            // so js/wasmJs/iOS test compilations stay free of ui-test (which they don't publish).
            implementation(libs.kotlin.test)
        }
        // Compose UI tests live only on JVM targets — runComposeUiTest API + Skiko native runtime (ADR-0011).
        getByName("jvmTest").dependencies {
            implementation(libs.compose.uiTest)
            implementation(compose.desktop.currentOs)
        }
        // Android host (JVM) Compose UI & config-change tests via Robolectric (ADR-0013).
        // activity-compose supplies the ComponentActivity that runComposeUiTest launches.
        getByName("androidHostTest").dependencies {
            implementation(libs.compose.uiTest)
            implementation(libs.robolectric)
            implementation(libs.androidx.test.core)
            implementation(libs.androidx.activity.compose)
        }
    }
}
