plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.dsh.miuixbrowser"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "dev.dsh.miuixbrowser"
        minSdk = 33
        targetSdk = 36
        versionCode = 118
        versionName = "v1.2.10-stable"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    signingConfigs {
        val keystorePath = System.getenv("MAFARI_KEYSTORE")
        if (keystorePath != null && rootProject.file(keystorePath).exists()) {
            create("release") {
                storeFile = rootProject.file(keystorePath)
                storePassword = System.getenv("MAFARI_STORE_PASS")
                keyAlias = System.getenv("MAFARI_KEY_ALIAS")
                keyPassword = System.getenv("MAFARI_KEY_PASS")
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            isMinifyEnabled = true
            isShrinkResources = true
            optimization {
                enable = true
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}


dependencies {
    // miuix 来自 miuix/ 目录的源码（feat/miuix-glass 分支），Maven 上还没有 glass 模块。
    implementation(project(":miuix-ui"))
    implementation(project(":miuix-preference"))
    implementation(project(":miuix-icons"))
    implementation(project(":miuix-glass-icons"))
    implementation(project(":miuix-squircle"))
    implementation(project(":miuix-blur"))
    implementation(project(":miuix-glass"))

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.webkit:webkit:1.14.0")
    implementation("androidx.browser:browser:1.8.0")
}
