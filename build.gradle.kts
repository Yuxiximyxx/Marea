plugins {
    id("com.android.application") version "9.5.0" apply false
    id("com.android.library") version "9.5.0" apply false
    id("org.jetbrains.kotlin.android") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.parcelize") version "2.4.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.21" apply false
    id("org.jetbrains.compose") version "1.12.2" apply false
    // 实验分支 exp/legado-reader：Legado 库模块用（版本对齐其原工程工具链）
    id("androidx.room") version "2.8.5" apply false
    id("com.google.devtools.ksp") version "2.3.12" apply false
}
