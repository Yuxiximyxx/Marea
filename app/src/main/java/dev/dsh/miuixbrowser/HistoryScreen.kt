package dev.dsh.miuixbrowser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun HistoryScreen(store: HistoryStore, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val backdrop = rememberLayerBackdrop()
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .layerBackdrop(backdrop)
                    .background(MiuixTheme.colorScheme.surface)
                    .navigationBarsPadding(),
            ) {
                if (store.items.isEmpty()) {
                    Text(
                        text = "还没有浏览记录",
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier
                            .padding(top = statusTop + 56.dp)
                            .padding(24.dp),
                    )
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(top = statusTop + 56.dp, bottom = 24.dp),
                    ) {
                        item {
                            SmallTitle(text = "最近访问")
                        }
                        items(store.items.size) { index ->
                            val entry = store.items[index]
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .padding(bottom = 8.dp),
                                insideMargin = PaddingValues(14.dp),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpen(entry.url) },
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.title,
                                            fontSize = 15.sp,
                                            color = MiuixTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = entry.url,
                                            fontSize = 11.sp,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp + statusTop)
                    .progressiveTopBar(
                        backdrop = backdrop,
                        tint = MiuixTheme.colorScheme.surface.copy(alpha = 0.62f),
                    ),
            ) {
                SmallTopAppBar(
                    title = "历史",
                    color = Color.Transparent,
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(imageVector = MiuixIcons.Back, contentDescription = "返回")
                        }
                    },
                    actions = {
                        if (store.items.isNotEmpty()) {
                            IconTextAction(
                                icon = MiuixIcons.Delete,
                                label = "清空",
                                modifier = Modifier.padding(end = 8.dp),
                            ) { store.clear() }
                        }
                    },
                    defaultWindowInsetsPadding = false,
                    modifier = Modifier.statusBarsPadding(),
                )
            }
        }
    }
}
