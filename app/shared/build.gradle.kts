import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    listOf(
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "Shared"
            isStatic = true
            // KAN-114-Et4: expose WalletConnectIos / WalletConnectBridge in the Shared framework headers
            // so the iOS Swift shim (reown-swift, KAN-121) can drive them. Requires the api() dep below.
            export(projects.walletconnect)
        }
    }
    
    jvm()
    
    js {
        browser()
    }
    
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }
    
    androidLibrary {
       namespace = "com.tneff.cyppie.app.shared"
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
            // Launch-check (walletExists) reads the encrypted seed file via :storage — only where it
            // exists (no js/wasm; KAN-89 seam). The live wallet shell (KAN-103) is likewise non-web.
            implementation(projects.storage)
            implementation(projects.feature.wallet)
            // KAN-114-Et4: embed WalletConnect on Android (toolchain-free since KAN-123 / AGP 9.1).
            implementation(projects.walletconnect)
        }
        jvmMain.dependencies {
            implementation(projects.storage)
            implementation(projects.feature.wallet)
        }
        iosMain.dependencies {
            implementation(projects.storage)
            implementation(projects.feature.wallet)
            // KAN-114-Et4: api (not implementation) so the framework can export() it to the Swift shim.
            api(projects.walletconnect)
        }
        commonMain.dependencies {
            api(projects.core)
            api(projects.feature.onboarding)
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)

            // DI — Koin (ADR-0007): the app is the composition root that aggregates feature modules.
            implementation(project.dependencies.platform(libs.koin.bom))
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jsMain.dependencies {
            implementation(libs.wrappers.browser)
        }
    }
}

dependencies {
    androidRuntimeClasspath(libs.compose.uiTooling)
}