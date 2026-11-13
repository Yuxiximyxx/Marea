package dev.dsh.miuixbrowser

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.glass.GlassDialogButtonRole
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 设置 → 隐私与安全 → 隐私模式（fork.5）：
 * 口令的设置 / 修改 / 清除（只存加盐 SHA-256，见 PrivacyVault）、当前状态、手动进 / 退。
 * 进入的正路是地址栏输入口令；这里的按钮是备用入口。
 *
 * fork.11 结构克隆：本页自 fork.5 起「组成完成后约 0.4~2 秒原生闪退（CRASH_NATIVE）」，
 * fork.9/10 两轮单点修复均被真机证伪、墓碑无线索。本版不做单点猜测，把整页按
 * 已在真机大量使用、确认不崩的链接分发页（DispatchScreen）逐件同构重写——
 * 骨架 / 顶栏 / 双 backdrop / 卡片 / 输入框一律与分发页同款同用法，只保留隐私
 * 业务内容与既有 Diag 步进日志，做结构级整体排除。与分发页对齐的取舍：
 * - 输入框用分发页编辑器的调用形态（miuix TextField）；fork.11 时口令为结构
 *   对齐一度**明文显示**，fork.12 已恢复掩码（PasswordVisualTransformation）
 *   与密码键盘，但当年随「密码三件套」一并摘掉的焦点监听修饰符**不带回**——
 *   它当年只挂诊断日志、本无功能必要；真凶未逐个隔离，这样恢复掩码最稳妥。
 * - 字段的获焦日志随焦点监听修饰符一并去除（三行）；其余步进日志保留。
 * - 清除口令确认弹窗移到 Scaffold 内容 Box 内（与分发页的编辑器 / 报告弹窗同位置）。
 *
 * exp.18 去玻璃化（二分修复）：exp.17 真机打开本页约 0.7~1.1 秒必 CRASH_NATIVE，
 * 墓碑实锤 SIGSEGV / SEGV_ACCERR +「likely stack overflow」——原生深递归把栈打爆，
 * Java 层无涉。本页自 fork.11 起与分发页同构、独有的一点是它在设置栈深层被打开后
 * 双 backdrop（miuix LayerBackdrop + Kyant LayerBackdrop）对内容层做自引用采样/录制。
 * 本版整页撤掉采样链做隔离验证：不再创建任何 backdrop、内容层不再挂 layerBackdrop
 * 修饰、不再向子组件注入 LocalLiquidBackdrop、顶栏换普通 TopAppBar、返回键与清除
 * 确认弹窗一律传 null backdrop 走实色回退。页面功能与文案一字未动；blurEnabled
 * 入参保留签名兼容，页内不再据此启用采样。若真机过关，后续再逐项回加定位真凶。
 */
@Composable
fun PrivacySettingsScreen(
    onBack: () -> Unit,
    blurEnabled: Boolean = true,
    // fork.9：由调用方传入（读 PrivacyMode 的进程级缓存），页面本身零 WebView/Profile
    // 原生接触——支持状态在 MainActivity 启动时主线程预热填充，本页只读传入值。
    profileSupported: Boolean = false,
) {
    // fork.7 步进日志：本页组合的最早一步——排在任何状态读取之前落盘
    remember { Diag.log("Privacy: screen composition entered"); true }
    // exp.18：去玻璃化——本页不再创建 / 采样任何 backdrop（见页头注释）。
    // blurEnabled 入参保留签名兼容，页内不再据此启用采样。
    val scrollBehavior = MiuixScrollBehavior()
    val topScrolled = rememberTopBarScrolled(scrollBehavior)

    // fork.6 防御：初始状态读取全部消毒——口令是否已设走 runCatching（异常按未设置处理）。
    val context = LocalContext.current
    var passwordSet by remember {
        mutableStateOf(runCatching { PrivacyVault.isSet(context) }.getOrDefault(false))
    }
    remember { Diag.log("PrivacySettingsScreen composed, passwordSet=$passwordSet, profile=$profileSupported"); true }
    // fork.7 步进日志：首帧真正组合完成才打这一行
    LaunchedEffect(Unit) { Diag.log("Privacy: screen first frame composed") }
    // fork.11 存活探针：纯日志、无其他副作用——页面活满 2.5 秒才打得出，是本版过关信号
    LaunchedEffect(Unit) {
        delay(2500)
        Diag.log("Privacy: page alive +2.5s")
    }
    // exp.18：补 +0.5s / +1.5s 两条同款纯日志探针，把既往 0.7~1.1 秒的死亡时间窗夹得更准
    LaunchedEffect(Unit) {
        delay(500)
        Diag.log("Privacy: page alive +0.5s")
    }
    LaunchedEffect(Unit) {
        delay(1500)
        Diag.log("Privacy: page alive +1.5s")
    }
    // fork.8 步进日志：页面销毁留一行——闪退前若没它而直接换 pid，死点在页面存活期间
    DisposableEffect(Unit) {
        onDispose { Diag.log("Privacy: screen disposed") }
    }
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            // exp.18：普通顶栏（无玻璃采样）；返回键传 null backdrop 走实色回退
            TopAppBar(
                title = "隐私模式",
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    LiquidGlassBackButton(
                        backdrop = null,
                        surfaceVisible = topScrolled,
                        onClick = { Diag.log("Privacy: click back"); onBack() },
                    )
                },
            )
        },
    ) { innerPadding ->
        val topInset = innerPadding.calculateTopPadding()
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
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
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 8.dp),
                            insideMargin = PaddingValues(14.dp),
                        ) {
                            // fork.8 步进日志：状态卡区块组合完成
                            remember { Diag.log("Privacy: block composed: status, passwordSet=$passwordSet"); true }
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
                                text = if (profileSupported) {
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
                                        onClick = { Diag.log("Privacy: click exit"); PrivacyMode.exit() },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                } else {
                                    AppTextButton(
                                        text = "进入隐私模式",
                                        onClick = {
                                            Diag.log("Privacy: click enter")
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
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 8.dp),
                            insideMargin = PaddingValues(14.dp),
                        ) {
                            // fork.8 步进日志：口令输入区区块组合完成
                            remember { Diag.log("Privacy: block composed: password"); true }
                            if (passwordSet) {
                                TextField(
                                    value = oldPw,
                                    onValueChange = { oldPw = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    label = "当前口令",
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    visualTransformation = PasswordVisualTransformation(),
                                )
                                Spacer(Modifier.height(10.dp))
                            }
                            TextField(
                                value = newPw,
                                onValueChange = { newPw = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = if (passwordSet) "新口令" else "设置口令",
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                visualTransformation = PasswordVisualTransformation(),
                            )
                            Spacer(Modifier.height(10.dp))
                            TextField(
                                value = newPw2,
                                onValueChange = { newPw2 = it },
                                modifier = Modifier.fillMaxWidth(),
                                label = "再输入一次",
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                visualTransformation = PasswordVisualTransformation(),
                            )
                            Spacer(Modifier.height(12.dp))
                            AppTextButton(
                                text = if (passwordSet) "修改口令" else "保存口令",
                                onClick = {
                                    Diag.log("Privacy: click save password, passwordSet=$passwordSet")
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
                                        .clickable {
                                            Diag.log("Privacy: click clear (open confirm)")
                                            showClearConfirm = true
                                        }
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
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 8.dp),
                            insideMargin = PaddingValues(14.dp),
                        ) {
                            // fork.8 步进日志：隔离范围区块组合完成
                            remember { Diag.log("Privacy: block composed: scope"); true }
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

            if (showClearConfirm) {
                AppGlassDialog(
                    onDismissRequest = { showClearConfirm = false },
                    show = true,
                    backdrop = null,
                    title = "清除口令",
                    summary = "清除后隐私模式关闭，地址栏输入原口令不再进入。已产生的隐私书签 / 历史 / 下载不会被删除。",
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        AppDialogButton(
                            onClick = { Diag.log("Privacy: click clear cancel"); showClearConfirm = false },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(text = "取消", style = MiuixTheme.textStyles.button)
                        }
                        Spacer(Modifier.width(12.dp))
                        AppDialogButton(
                            onClick = {
                                Diag.log("Privacy: click clear confirm")
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
}
