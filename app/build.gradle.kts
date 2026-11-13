plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// ---- 依赖版本：统一在此声明 ----
// 既用于下方 dependencies，也会写进 BuildConfig.DEP_VERSIONS，供「关于」页的「依赖版本」展示（改版本只需改这里）。
val miuixVersion = "0.9.4"                 // miuix 源码在 ./miuix，由 includeBuild 参与构建，此处为其版本号
val backdropVersion = "2.0.1"
val activityComposeVersion = "1.13.0"
val coreKtxVersion = "1.19.1"
val webkitVersion = "1.18.0"
val credentialsVersion = "1.6.0"
val browserVersion = "1.10.0"
val navigationEventVersion = "1.2.0"
val okhttpVersion = "4.12.0"
// 构建工具链：Kotlin / Compose Multiplatform / AGP 的版本声明在根 build.gradle.kts（miuix 的在 miuix/gradle/libs.versions.toml），
// Gradle 版本在 gradle/wrapper/gradle-wrapper.properties；升级时请同步这里，仅用于「关于」页展示
val kotlinVersion = "2.4.21"
val composeMultiplatformVersion = "1.12.2"
val agpVersion = "9.5.0"

// 格式：分组|名称|版本;分组|名称|版本…（分组用 ASCII 标识，界面文案由 AboutScreen 映射）
val dependencyVersions = listOf(
    Triple("ui", "miuix-glass", miuixVersion),
    Triple("ui", "Backdrop", backdropVersion),
    Triple("ui", "Compose Multiplatform", composeMultiplatformVersion),
    Triple("androidx", "Activity Compose", activityComposeVersion),
    Triple("androidx", "Core KTX", coreKtxVersion),
    Triple("androidx", "WebKit", webkitVersion),
    Triple("androidx", "Credentials", credentialsVersion),
    Triple("androidx", "Browser", browserVersion),
    Triple("androidx", "NavigationEvent", navigationEventVersion),
    Triple("net", "OkHttp", okhttpVersion),
    Triple("build", "Kotlin", kotlinVersion),
    Triple("build", "Android Gradle Plugin", agpVersion),
    Triple("build", "Gradle", gradle.gradleVersion),
).joinToString(";") { (group, name, version) -> "$group|$name|$version" }

android {
    namespace = "dev.dsh.miuixbrowser"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "io.github.yuxiximyxx.marea"
        minSdk = 33
        targetSdk = 37
        versionCode = 292
        versionName = "v1.8.28-exp.18"
        buildConfigField("String", "DEP_VERSIONS", "\"$dependencyVersions\"")
        // exp.8 瘦身：原生库只留 arm64-v8a（仍出单个通用 APK，不用 splits）
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        // 实验分支：legado 库模块要求消费方开启 core library desugaring
        isCoreLibraryDesugaringEnabled = true
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
                // exp.8 瘦身：打开 R8（exp.1 为保真而关，靠 legado 模块接回的 consumer
                // 规则与 app/proguard-rules.pro 的 exp.8 补充规则覆盖 Cronet 等反射面），
                // 正式线 main 不受影响
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
            // hutool-core 与 hutool-crypto 都带 JarIndex，合并时冲突，排除
            excludes += "/META-INF/INDEX.LIST"
        }
    }
}

// exp.5：material-color-utilities 必须钉死 5.0.1，不许冲突仲裁升级。
// material-kolor 5.0.1 的字节码引用 com/materialkolor/scheme/DynamicScheme$Platform
//（5.0.1 版 utilities 里该类在 scheme 包，属过渡布局：dynamiccolor 下已有
// ColorSpec/DynamicScheme、scheme 下的 DynamicScheme 仍保留）；而 includeBuild 的
// miuix-ui 按其 catalog 引入 utilities 6.0.0——6.0.0 起 scheme.DynamicScheme 被
// 删除（只剩 dynamiccolor.DynamicScheme）。仲裁取高版 6.0.0 时 utilities 能进包、
// 唯独 kolor 要的那个类不在，开书照样 NoClassDefFoundError（exp.5 首发实测：
// SchemeTonalSpot/Hct 已定义、DynamicScheme$Platform 仍缺，门禁拦截后补此钉版）。
// kolor 5.0.1 的两处跨包引用（scheme.DynamicScheme$Platform 与
// dynamiccolor.ColorSpec$SpecVersion）在 5.0.1 里都有定义。反面：miuix-ui 的
// ThemeController 按 6.0.0 布局引用 dynamiccolor.DynamicScheme，该类不在 5.0.1
// 中——但其动态配色生成路径在 Marea 里没有被接线（exp.1–exp.4 在 utilities
// 整包缺失时浏览器一直正常使用可证），属存量休眠不一致、非本钉版引入的回归；
// 将来接线 miuix 动态配色前须先处理此冲突（届时考虑升 kolor 到与 6.0.0 对齐的版本）。
// exp.6 更正与了结：上面「ThemeController 路径在 Marea 里没有被接线」的判断错误——
// 它经 Legado 的 ThemeColorSchemeOverride（MiuixTheme 包裹阅读页）接着线，exp.5
// 真机即崩在 ThemeController.colorsFromSeed 调 dynamiccolor/DynamicScheme$Platform
// （5.0.1 无此类）。总根因是本地 miuix 源码与 kolor 的 utilities 布局错位：miuix
// 源码按 utilities 6.0.0 编写（dynamiccolor 布局 + SPEC_2026/SchemeCmf），kolor
// 5.0.1 按 5.0.1 编写（scheme 布局），单一 utilities 版本无解。本版将本地 miuix
// 源码对齐回 5.0.1（miuix 版本目录的 utilities 改 5.0.1；ThemeController 改用
// scheme 包 DynamicScheme 与九个 Scheme 的 5.0.1 同形签名；5.0.1 没有的
// SchemeCmf/SPEC_2026 不再引用、Cmf 风格以 TonalSpot 兜底），与 Legado 原工程的
// miuix 0.9.4 + kolor 5.0.1 + utilities 5.0.1 自洽组合一致。本 force 保留作护栏。
configurations.configureEach {
    resolutionStrategy {
        force("com.materialkolor:material-color-utilities:5.0.1")
        force("com.materialkolor:material-color-utilities-android:5.0.1")
    }
}

dependencies {
    // 实验分支 exp/legado-reader：整体移植的 Legado 阅读器（库模块）
    implementation(project(":legado"))
    // exp.7：共享玻璃原语（glassBar / Marea 材质档位桥），legado 模块同依赖这一份
    implementation(project(":glasskit"))
    implementation(libs.kotlinx.collections.immutable)
    coreLibraryDesugaring(libs.desugar)
    // 宿主 LegadoReaderHostActivity 的编译面（Nav3 / Koin / AppCompat）
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.compose.viewmodel)
    implementation(libs.appcompat.appcompat)

    implementation("top.yukonga.miuix.kmp:miuix-ui-android:$miuixVersion")
    implementation("top.yukonga.miuix.kmp:miuix-preference-android:$miuixVersion")
    implementation("top.yukonga.miuix.kmp:miuix-icons-android:$miuixVersion")
    implementation("top.yukonga.miuix.kmp:miuix-squircle-android:$miuixVersion")
    implementation("top.yukonga.miuix.kmp:miuix-blur-android:$miuixVersion")
    implementation("top.yukonga.miuix.kmp:miuix-glass-android:$miuixVersion")
    implementation("top.yukonga.miuix.kmp:miuix-glass-icons-android:$miuixVersion")
    implementation("top.yukonga.miuix.kmp:miuix-nav-android:$miuixVersion")
    // Kyant0/AndroidLiquidGlass（KMP 版 2.x，Android 变体）
    implementation("io.github.kyant0:backdrop-android:$backdropVersion")

    implementation("androidx.activity:activity-compose:$activityComposeVersion")
    implementation("androidx.core:core-ktx:$coreKtxVersion")
    implementation("androidx.webkit:webkit:$webkitVersion")
    // Passkey：WebView 通过 Credential Manager 调起系统 / 密码管理器的 Passkey 界面
    implementation("androidx.credentials:credentials:$credentialsVersion")
    implementation("androidx.credentials:credentials-play-services-auth:$credentialsVersion")
    implementation("androidx.browser:browser:$browserVersion")
    implementation("androidx.navigationevent:navigationevent-compose:$navigationEventVersion")
    // WebDAV 同步（TxtSync）：需要 MKCOL 等任意 HTTP 方法，HttpURLConnection 不支持
    implementation("com.squareup.okhttp3:okhttp:$okhttpVersion")
}

