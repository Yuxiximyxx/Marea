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
        versionCode = 133
        versionName = "v1.3.3-dev"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
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
    val miuix = "0.9.4"
    implementation("top.yukonga.miuix.kmp:miuix-ui-android:$miuix")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:$miuix")
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:$miuix")
    implementation("top.yukonga.miuix.kmp:miuix-squircle-android:$miuix")
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:$miuix")
    implementation("top.yukonga.miuix.kmp:miuix-glass-android:$miuix")
    implementation("top.yukonga.miuix.kmp:miuix-glass-icons-android:$miuix")
    implementation("top.yukonga.miuix.kmp:miuix-nav-android:$miuix")

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.webkit:webkit:1.14.0")
    // Passkey：WebView 通过 Credential Manager 调起系统 / 密码管理器的 Passkey 界面
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.navigationevent:navigationevent-compose:1.2.0")
}
