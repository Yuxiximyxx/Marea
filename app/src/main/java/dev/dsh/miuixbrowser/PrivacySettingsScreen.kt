package dev.dsh.miuixbrowser

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.asPaddingValues
import com.kyant.backdrop.backdrops.layerBackdrop as kyantLayerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop as rememberKyantBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior

/**
 * 设置 → 隐私与安全 → 隐私模式（fork.5）：
 * 口令的设置 / 修改 / 清除（只存加盐 SHA-256，见 PrivacyVault）、当前状态、手动进 / 退。
 * 进入的正路是地址栏输入口令；这里的按钮是备用入口。
 */
@Composable
fun PrivacySettingsScreen(
    onBack: () -> Unit,
    blurEnabled: Boolean = true,
) {
    val context = LocalContext.current
    val backdrop = rememberLayerBackdrop()
    val liquidBackdrop = rememberKyantBackdrop()
    val glass = rememberGlassActive(blurEnabled)
    val liquid = if (glass) liquidBackdrop else null
    val scrollBehavior = MiuixScrollBehavior()
    val topScrolled = rememberTopBarScrolled(scrollBehavior)

    var passwordSet by remember { mutableStateOf(PrivacyVault.isSet(context)) }
    var oldPw by remember { mutableStateOf("") }
    var newPw by remember { mutableStateOf("") }
    var newPw2 by remember { mutableStateOf("") }
    var showClearConfirm by remember { mutableStateOf(false) }

    fun toast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    fun validateNew(): Boolean = when {
        newPw.isEmpty() -> {
            toast("口令不能为空")
            false
        }
        newPw != newPw2 -> {
            toast("两次输入的口令不一致")
            false
        }
        else -> true
    }

    CompositionLocalProvider(LocalLiquidBackdrop provides if (glass) liquidBackdrop else null) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                PageGlassTopBar(
                    title = "隐私模式",
                    backdrop = backdrop,
                    glass = glass,
                    scrollBehavior = scrollBehavior,
                    onBack = onBack,
                    backButton = {
                        LiquidGlassBackButton(backdrop = liquid, surfaceVisible = topScrolled, onClick = onBack)
                    },
                )
            },
        ) { innerPadding ->
            val topInset = innerPadding.calculateTopPadding()
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .layerBackdrop(backdrop)
                        .kyantLayerBackdrop(liquidBackdrop)
                        .nestedScroll(scrollBehavior.nestedScrollConnection)
                        .background(MiuixTheme.colorScheme.surface),
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(
                            top = topInset,
                            bottom = 24.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding(),
                        ),
                    ) {
                        item {
                            SmallTitle(text = "状态")
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp),
                                insideMargin = PaddingValues(16.dp),
                            ) {
                                val status = when {
                                    !passwordSet -> "未设置口令"
                                    PrivacyMode.active -> "已开启 · 当前处于隐私模式"
                                    else -> "已设置口令 · 当前未开启"
                                }
                                Text(text = status, fontSize = 16.sp, color = MiuixTheme.colorScheme.onSurface)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "进入方式：在地址栏输入口令并确认。输错会当作普通搜索词处理，不会有任何提示。",
                                    fontSize = 13.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = if (PrivacyMode.profileSupported()) {
                                        "Cookie 隔离：已启用 WebView 多 Profile，隐私空间使用独立 Cookie 与网站存储。"
                                    } else {
                                        "Cookie 隔离：当前设备的 WebView 不支持多 Profile，Cookie 与普通模式共享；书签 / 历史 / 下载仍完全独立。"
                                    },
                                    fontSize = 13.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                                if (passwordSet) {
                                    Spacer(Modifier.height(12.dp))
                                    if (PrivacyMode.active) {
                                        AppTextButton(
                                            text = "退出隐私模式",
                                            onClick = { PrivacyMode.exit() },
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    } else {
                                        AppTextButton(
                                            text = "进入隐私模式",
                                            onClick = {
                                                PrivacyMode.enter()
                                                onBack()
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            primary = true,
                                        )
                                    }
                                }
                            }
                        }
                        item {
                            SmallTitle(text = "口令")
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp),
                                insideMargin = PaddingValues(16.dp),
                            ) {
                                if (passwordSet) {
                                    TextField(
                                        value = oldPw,
                                        onValueChange = { oldPw = it },
                                        label = "当前口令",
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        visualTransformation = PasswordVisualTransformation(),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    )
                                    Spacer(Modifier.height(10.dp))
                                }
                                TextField(
                                    value = newPw,
                                    onValueChange = { newPw = it },
                                    label = if (passwordSet) "新口令" else "设置口令",
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                )
                                Spacer(Modifier.height(10.dp))
                                TextField(
                                    value = newPw2,
                                    onValueChange = { newPw2 = it },
                                    label = "再输入一次",
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                )
                                Spacer(Modifier.height(12.dp))
                                AppTextButton(
                                    text = if (passwordSet) "修改口令" else "保存口令",
                                    onClick = {
                                        if (passwordSet && !PrivacyVault.verify(context, oldPw)) {
                                            toast("当前口令不正确")
                                        } else if (validateNew()) {
                                            PrivacyVault.setPassword(context, newPw)
                                            passwordSet = true
                                            oldPw = ""
                                            newPw = ""
                                            newPw2 = ""
                                            toast("口令已保存")
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    primary = true,
                                )
                                if (passwordSet) {
                                    Spacer(Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showClearConfirm = true }
                                            .padding(vertical = 10.dp),
                                    ) {
                                        Text(
                                            text = "清除口令并关闭隐私模式",
                                            fontSize = 15.sp,
                                            color = MiuixTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                        item {
                            SmallTitle(text = "隔离范围")
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp),
                                insideMargin = PaddingValues(16.dp),
                            ) {
                                Text(
                                    text = "隐私模式有自己独立的一套书签、历史和下载列表，与普通模式互不可见；" +
                                        "隐私下载的文件保存在 Download/Marea/Private。切到后台或锁屏会自动退出隐私模式，" +
                                        "正在进行的下载不会被中断，记录留在隐私空间。口令只保存加盐哈希，应用里不存在明文。",
                                    fontSize = 13.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showClearConfirm) {
            AppGlassDialog(
                onDismissRequest = { showClearConfirm = false },
                show = true,
                backdrop = if (glass) backdrop else null,
                title = "清除口令",
                summary = "清除后隐私模式关闭，地址栏输入原口令不再进入。已产生的隐私书签 / 历史 / 下载不会被删除。",
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    AppDialogButton(
                        onClick = { showClearConfirm = false },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(text = "取消", style = MiuixTheme.textStyles.button)
                    }
                    Spacer(Modifier.width(12.dp))
                    AppDialogButton(
                        onClick = {
                            showClearConfirm = false
                            PrivacyVault.clear(context)
                            PrivacyMode.exit()
                            passwordSet = false
                            oldPw = ""
                            newPw = ""
                            newPw2 = ""
                            toast("口令已清除")
                        },
                        modifier = Modifier.weight(1f),
                        role = GlassDialogButtonRole.Primary,
                    ) {
                        Text(text = "清除", style = MiuixTheme.textStyles.button)
                    }
                }
            }
        }
    }
}
