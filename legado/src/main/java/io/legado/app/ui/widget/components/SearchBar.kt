package io.legado.app.ui.widget.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.icon.AppIcons
import io.legado.app.ui.widget.components.text.AppText
import kotlinx.coroutines.CoroutineScope
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SearchBar(
    modifier: Modifier = Modifier,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit = {},
    placeholder: String? = null,
    leadingIcon: @Composable (() -> Unit)? = {
        AppIcon(
            modifier = Modifier.padding(horizontal = 12.dp),
            imageVector = AppIcons.Search,
            contentDescription = null
        )
    },
    backgroundColor: Color = Color.Unspecified,
    scrollState: LazyListState? = null,
    scope: CoroutineScope = rememberCoroutineScope(),
    trailingIcon: @Composable (() -> Unit)? = null,
    autoFocus: Boolean = true,
    dropdownMenu: (@Composable (onDismiss: () -> Unit) -> Unit)? = null
) {
    val textFieldState = rememberTextFieldState(initialText = query)
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var hasFocused by rememberSaveable { mutableStateOf(false) }

    val submitSearch: (String) -> Unit = { value ->
        onSearch(value)
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
    }

    LaunchedEffect(autoFocus) {
        if (autoFocus && !hasFocused) {
            focusRequester.requestFocus()
            // 某些情况下需要手动调用 show() 确保键盘弹出
            keyboardController?.show()
            hasFocused = true
        } else if (!autoFocus) {
            focusManager.clearFocus(force = true)
            keyboardController?.hide()
        }
    }

    LaunchedEffect(query) {
        if (query != textFieldState.text.toString()) {
            textFieldState.edit {
                replace(0, length, query)
            }
        }
    }

    LaunchedEffect(textFieldState.text) {
        snapshotFlow { textFieldState.text.toString() }
            .collect { newText ->
                if (newText != query) {
                    onQueryChange(newText)
                }
            }
    }

    // exp.12：引擎分支已去除，只保留原 miuix 分支（Miuix SearchBar 输入形态）
    val resolvedBackgroundColor = if (backgroundColor != Color.Unspecified) {
        backgroundColor
    } else {
        MiuixTheme.colorScheme.surfaceContainer
    }
    val resolvedPlaceholder = placeholder ?: stringResource(R.string.search_placeholder)

    AppDenseTextField(
        state = textFieldState,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .focusRequester(focusRequester),
        placeholder = { AppText(resolvedPlaceholder) },
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        onKeyboardAction = {
            submitSearch(textFieldState.text.toString())
        },
        lineLimits = TextFieldLineLimits.SingleLine,
        backgroundColor = resolvedBackgroundColor,
        miuixUseSearchBarInputField = true,
        miuixSearchBarLabel = resolvedPlaceholder,
        miuixOnSearch = submitSearch,
    )
}
