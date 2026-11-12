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
    implementation(libs.material.kolor)
    implementation(libs.haze.core)
    implementation(libs.haze.blur)
    implementation(libs.miuix.ui.android)
    implementation(libs.miuix.preference.android)
    implementation(libs.miuix.icons.android)
    implementation(libs.miuix.blur.android)
    implementation(libs.miuix.core)
    implementation(libs.capsule)
    implementation(libs.backdrop)
    implementation(libs.lyricViewx)
    implementation(libs.timber)
}
