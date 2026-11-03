// Vendored from compose-miuix-ui/miuix (feat/miuix-glass, PR #423), Apache-2.0.
// Android-only build of the module; see /miuix/README.md.
plugins {
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    jvmToolchain(17)

    android {
        namespace = "top.yukonga.miuix.kmp.shader"
        compileSdk {
            version = release(37) {
                minorApiLevel = 0
            }
        }
        buildToolsVersion = "36.0.0"
        minSdk = 24
    }

    sourceSets {
        commonMain.dependencies {
            implementation("org.jetbrains.compose.foundation:foundation:1.12.2")
        }
    }
}
