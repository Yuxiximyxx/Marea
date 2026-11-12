# mafari proguard rules
-dontwarn org.jetbrains.compose.**
-keep class dev.dsh.miuixbrowser.** { *; }

# ---- exp.8 瘦身补充规则（R8 开启后 Legado 反射面的兜底；legado 模块自带的两份 ----
# ---- 规则已由 legado/build.gradle.kts 接回为 consumer 规则，这里只补它们没覆盖的） ----

# material-kolor 家族整体保名保留：exp.1–exp.6 的缺类教训在前，且发版闭合门禁
# （tools/check_materialkolor_closure.py）需按原始描述符核对引用/定义，混淆改名会
# 使门禁失效；该库体量小，全保留代价可控
-keep class com.materialkolor.** { *; }

# kotlinx.serialization：@Serializable 类的 Companion / serializer() / 生成序列化器
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep class **$$serializer { *; }
-dontnote kotlinx.serialization.**

# Room：数据库类经 Room 反射实例化其生成实现；DAO 等生成类一并保名保留
-keep class * extends androidx.room.RoomDatabase { *; }
-keep class io.legado.app.data.AppDatabase { *; }
-keep class **_Impl { *; }

# Legado 应用入口（宿主 Application，manifest 已声明、这里再保一道）
-keep class io.legado.app.App { *; }

# 阅读入口组合函数所在文件类（自检按原名核对，保持类名不改）
-keep class io.legado.app.ui.book.read.ReadBookRouteScreenKt { *; }

# Rhino 从 JS 按名反射调用的规则解析面（书源脚本的 java.* 调用落点）
-keep class io.legado.app.model.analyzeRule.** { *; }

# Koin：经反射构造 ViewModel 与部分定义，整体保留（库体量小）
-keep class org.koin.** { *; }

# WebView @JavascriptInterface 回调方法不许改名/移除
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Parcelable / Serializable 兜底（默认规则之外再保一道）
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
