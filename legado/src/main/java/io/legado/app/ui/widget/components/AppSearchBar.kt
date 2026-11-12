package io.legado.app.ui.widget.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import top.yukonga.miuix.kmp.basic.InputField as MiuixInputField
import top.yukonga.miuix.kmp.basic.SearchBar as MiuixSearchBar

@Composable
fun AppSearchBar(
    modifier: Modifier = Modifier,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    label: String,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {

    var internalExpanded by remember { mutableStateOf(expanded) }
    var internalQuery by remember { mutableStateOf(query) }

    LaunchedEffect(expanded) {
        internalExpanded = expanded
    }

    LaunchedEffect(query) {
        if (query != internalQuery) {
            internalQuery = query
        }
    }

    MiuixSearchBar(
        modifier = modifier,
        inputField = {
            MiuixInputField(
                query = internalQuery,
                onQueryChange = { newQuery ->
                    if (newQuery.isNotEmpty() || internalExpanded) {
                        internalQuery = newQuery
                        onQueryChange(newQuery)
                    }
                },
                onSearch = {
                    onSearch(it)
                    onExpandedChange(false)
                },
                expanded = internalExpanded,
                onExpandedChange = { newExpanded ->
                    internalExpanded = newExpanded
                    onExpandedChange(newExpanded)
                },
                label = label,
                leadingIcon = leadingIcon,
                trailingIcon = trailingIcon,
            )
        },
        expanded = internalExpanded,
        onExpandedChange = onExpandedChange,
        content = content,
    )
}
