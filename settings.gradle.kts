pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 实验分支 exp/legado-reader：Legado 的 com.github.* 依赖走 JitPack
        maven("https://jitpack.io")
    }
}

// 本地 miuix-glass 源码：把 Maven 坐标替换为 includeBuild 内的工程，
// 无需先 publishToMavenLocal 即可编译 glass 组件。
includeBuild("miuix") {
    dependencySubstitution {
        substitute(module("top.yukonga.miuix.kmp:miuix-core-android")).using(project(":miuix-core"))
        substitute(module("top.yukonga.miuix.kmp:miuix-shader-android")).using(project(":miuix-shader"))
        substitute(module("top.yukonga.miuix.kmp:miuix-ui-android")).using(project(":miuix-ui"))
        substitute(module("top.yukonga.miuix.kmp:miuix-preference-android")).using(project(":miuix-preference"))
        substitute(module("top.yukonga.miuix.kmp:miuix-icons-android")).using(project(":miuix-icons"))
        substitute(module("top.yukonga.miuix.kmp:miuix-glass-icons-android")).using(project(":miuix-glass-icons"))
        substitute(module("top.yukonga.miuix.kmp:miuix-squircle-android")).using(project(":miuix-squircle"))
        substitute(module("top.yukonga.miuix.kmp:miuix-blur-android")).using(project(":miuix-blur"))
        substitute(module("top.yukonga.miuix.kmp:miuix-glass-android")).using(project(":miuix-glass"))
        substitute(module("top.yukonga.miuix.kmp:miuix-nav-android")).using(project(":miuix-nav"))
    }
}

rootProject.name = "MiuixBrowser"
include(":app")
// 实验分支 exp/legado-reader：Legado 整体移植（库模块 + 其 book/rhino 子模块）
include(":legado")
include(":modules:book")
include(":modules:rhino")
