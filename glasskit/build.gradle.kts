// exp.7 共享玻璃原语模块：app 与 legado 两边都依赖，避免玻璃实现出现两份。
// 内容只有三件：Marea 材质档位（LocalMareaBlurMode + 落盘桥）、Modifier.glassBar
// （自 app 的 BrowserBottomBar 下沉，逐字搬移）、档位常量。
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.dsh.glasskit"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }
    buildToolsVersion = "36.0.0"

    defaultConfig {
        minSdk = 33
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.runtime)
    api(libs.androidx.compose.foundation)
    // 与 app 模块同款 -android 后缀坐标（0.9.4，includeBuild 替换为本地 miuix 源码树）
    api("top.yukonga.miuix.kmp:miuix-ui-android:0.9.4")
    api("top.yukonga.miuix.kmp:miuix-glass-android:0.9.4")
    api("top.yukonga.miuix.kmp:miuix-blur-android:0.9.4")
    api("io.github.kyant0:backdrop-android:2.0.1")
}
