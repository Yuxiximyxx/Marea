// 实验分支 exp/legado-reader：Legado（HapeLee/legado-with-MD3 @ bba5c64）整体源码
// 以 Android Library 形态并入 Marea。namespace 保持 io.legado.app，使其 R /
// BuildConfig / Koin / Room 生成代码与原工程同构；依赖与原 app 模块对齐
// （去掉 google-services / baseline-profile / 测试依赖等应用级项）。
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.parcelize")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("androidx.room")
    id("com.google.devtools.ksp")
}

android {
    namespace = "io.legado.app"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }
    buildToolsVersion = "36.0.0"

    defaultConfig {
        minSdk = 33
        // 原工程 BuildConfig 字段（其代码引用 Cronet 版本与 VERSION_NAME /
        // APPLICATION_ID；库模块不自动生成后两者，这里显式补同名常量字段）
        buildConfigField("String", "Cronet_Version", "\"128.0.6613.40\"")
        buildConfigField("String", "Cronet_Main_Version", "\"128.0.0.0\"")
        buildConfigField("String", "VERSION_NAME", "\"3.26.19\"")
        buildConfigField("String", "APPLICATION_ID", "\"io.github.yuxiximyxx.marea\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
        viewBinding = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

ksp {
    arg("room.incremental", "true")
    arg("room.expandProjection", "true")
    arg("room.generateKotlin", "false")
}

dependencies {
    implementation(libs.androidx.profileinstaller)
    coreLibraryDesugaring(libs.desugar)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.bundles.coroutines)
    implementation(libs.core.ktx)
    implementation(libs.appcompat.appcompat)
    implementation(libs.activity.ktx)
    implementation(libs.fragment.ktx)
    implementation(libs.preference.ktx)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.webkit)
    implementation(libs.material)
    implementation(libs.flexbox)
    implementation(libs.gson)
    implementation(libs.lifecycle.common.java8)
    implementation(libs.lifecycle.service)
    implementation(libs.media.media)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.datasource.okhttp)
    implementation(libs.splitties.appctx)
    implementation(libs.splitties.systemservices)
    implementation(libs.splitties.views)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.liveeventbus)
    implementation(libs.jsoup)
    implementation(libs.json.path)
    implementation(libs.jsoupxpath)
    implementation(libs.intellij.markdown)
    implementation(project(":modules:book"))
    implementation(project(":modules:rhino"))
    implementation(libs.okhttp)
    implementation(fileTree(mapOf("dir" to "cronetlib", "include" to listOf("*.jar", "*.aar"))))
    implementation(libs.protobuf.javalite)
    implementation(libs.glide.glide)
    implementation(libs.glide.okhttp)
    ksp(libs.glide.ksp)
    implementation(libs.androidsvg)
    implementation(libs.glide.svg)
    implementation(libs.glide.recyclerview)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.serialization.gson)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.websockets)
    implementation(libs.zxing.lite)
    implementation(libs.colorpicker)
    implementation(libs.colorpicker.compose)
    implementation(libs.libarchive)
    implementation(libs.commons.text)
    implementation(libs.markwon.core)
    implementation(libs.markwon.image.glide)
    implementation(libs.markwon.ext.tables)
    implementation(libs.markwon.html)
    implementation(libs.quick.chinese.transfer.core)
    implementation(libs.hutool.crypto)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.perf)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.palette)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.startup.runtime)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui.text)
    implementation(libs.androidx.compose.ui.util)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.material.ripple)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.gif)
    implementation(libs.coil.svg)
    implementation(libs.coil.network.okhttp)
    implementation(libs.telephoto.zoomable.image.coil3)
    implementation(libs.accompanist.webview)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.datetime)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.constraintlayout.compose)
    implementation(libs.androidx.compose.ui.viewbinding)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.navigationevent)
    implementation(libs.androidx.navigationevent.compose)
    implementation(libs.androidx.compose.adaptive)
    implementation(libs.androidx.compose.adaptive.layout)
    implementation(libs.androidx.compose.adaptive.navigation)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.compose.material)
    implementation(libs.compose.materialIcons)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.core)
    implementation(libs.koin.android)
    implementation(libs.koin.compose)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.reorderable)
    // exp.4：catalog 的 material-kolor 是无后缀 KMP 根坐标，在本模块（Android 库）
    // 落到的变体缺 android 平台类——真机开书组合阅读主题时
    // NoClassDefFoundError: com/materialkolor/scheme/DynamicScheme$Platform 闪退
    // （exp.1 起的老问题，真机堆栈实锤）。与 miuix-core 同病同法：改用 -android
    // 后缀坐标（版本同 catalog materialKolor=5.0.1）
    implementation("com.materialkolor:material-kolor-android:5.0.1")
    // exp.5：exp.4 只改了 kolor 本体还不够——真机开书仍崩同一处（exp.4 APK 实测
    // 全包扫描：DynamicScheme$Platform 只出现 1 次、纯调用方引用，SchemeTonalSpot
    // 与 Hct 为 0，material-color-utilities 的类根本没进包）。Maven 元数据实查：
    // material-kolor-android:5.0.1 传递依赖的是 utilities 的无后缀根坐标
    // com.materialkolor:material-color-utilities:5.0.1，这一跳在本仓解析环境下
    // 同样落不到 android 变体被丢掉（原工程纯 Android 模块变体自动匹配、无事）。
    // 绕开传递跳、显式直达 -android 坐标，确保 utilities 的 android AAR 进
    // runtime 类路径；发版前另以 dex 类定义存在性作硬门禁验过才发。
    implementation("com.materialkolor:material-color-utilities-android:5.0.1")
    implementation(libs.haze.core)
    implementation(libs.haze.blur)
    // exp.7：Marea 材质档位桥 + 共享 glassBar（dev.dsh.glasskit），与 app 同一份实现
    implementation(project(":glasskit"))
    implementation(libs.miuix.ui.android)
    implementation(libs.miuix.preference.android)
    implementation(libs.miuix.icons.android)
    implementation(libs.miuix.blur.android)
    // catalog 的 miuix-core 是无后缀 KMP 根坐标，在本仓 includeBuild 替换下
    // 落不到 android 变体；改用与 app 模块同款的 -android 后缀坐标（0.9.4）
    implementation("top.yukonga.miuix.kmp:miuix-core-android:0.9.4")
    implementation(libs.capsule)
    implementation(libs.backdrop)
    implementation(libs.lyricViewx)
    implementation(libs.timber)
}
