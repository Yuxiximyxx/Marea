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
    }
}

rootProject.name = "MiuixBrowser"
include(":app")

// Vendored miuix (feat/miuix-glass, upstream PR #423): glass modules are not on Maven yet,
// and the branch also changes miuix-ui / miuix-blur, so the whole Android slice is built from source.
listOf(
    "miuix-core",
    "miuix-shader",
    "miuix-squircle",
    "miuix-blur",
    "miuix-ui",
    "miuix-preference",
    "miuix-icons",
    "miuix-glass-icons",
    "miuix-glass",
).forEach { name ->
    include(":$name")
    project(":$name").projectDir = file("miuix/$name")
}
