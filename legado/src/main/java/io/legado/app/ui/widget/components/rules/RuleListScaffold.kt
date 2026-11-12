package io.legado.app.ui.widget.components.rules

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.ui.widget.components.ActionItem
import io.legado.app.ui.widget.components.AppFloatingActionButton
import io.legado.app.ui.widget.components.SelectionActions
import io.legado.app.ui.widget.components.alert.AppAlertDialog
import io.legado.app.ui.widget.components.icon.AppIcon
import io.legado.app.ui.widget.components.list.ListScaffold
import io.legado.app.ui.widget.components.list.ListUiState
import io.legado.app.ui.widget.components.text.AppText
import io.legado.app.ui.widget.components.topbar.GlassTopAppBarScrollBehavior
import top.yukonga.miuix.kmp.basic.SnackbarHostState

@Composable
fun <T> RuleListScaffold(
    title: String,
    state: ListUiState<T>,
    subtitle: String? = null,
    onBackClick: () -> Unit,
    onSearchToggle: (Boolean) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    searchPlaceholder: String? = null,
    topBarActions: @Composable RowScope.() -> Unit = {},
    bottomContent: @Composable (ColumnScope.(GlassTopAppBarScrollBehavior) -> Unit)? = null,
    dropDownMenuContent: (@Composable ColumnScope.(dismiss: () -> Unit) -> Unit)? = null,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onSelectInvert: () -> Unit,
    selectionSecondaryActions: List<ActionItem>,
    onDeleteSelected: (Set<Any>) -> Unit,
    onAddClick: (() -> Unit)? = null,
    floatingActionButton: @Composable () -> Unit = {
        onAddClick?.let { onClick ->
            AnimatedVisibility(
                visible = state.selectedIds.isEmpty(),
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f)
            ) {
                AppFloatingActionButton(
                    onClick = onClick,
                    tooltipText = stringResource(R.string.add),
                    icon = Icons.Default.Add
                )
            }
        }
    },
    snackbarHostState: SnackbarHostState,
    content: @Composable (PaddingValues) -> Unit
) {
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    AppAlertDialog(
        show = showDeleteConfirmDialog,
        onDismissRequest = { showDeleteConfirmDialog = false },
        title = stringResource(R.string.delete),
        text = stringResource(R.string.sure_del),
        confirmText = stringResource(R.string.ok),
        onConfirm = {
            onDeleteSelected(state.selectedIds)
            showDeleteConfirmDialog = false
        },
        dismissText = stringResource(R.string.cancel),
        onDismiss = { showDeleteConfirmDialog = false }
    )

    ListScaffold(
        title = title,
        subtitle = subtitle,
        state = state,
        onBackClick = onBackClick,
        onSearchToggle = onSearchToggle,
        onSearchQueryChange = onSearchQueryChange,
        searchPlaceholder = searchPlaceholder,
        topBarActions = topBarActions,
        bottomContent = bottomContent,
        dropDownMenuContent = dropDownMenuContent,
        onAddClick = onAddClick,
        floatingActionButton = floatingActionButton,
        snackbarHostState = snackbarHostState,
        selectionActions = SelectionActions(
            onClearSelection = onClearSelection,
            onSelectAll = onSelectAll,
            onSelectInvert = onSelectInvert,
            primaryAction = ActionItem(
                text = stringResource(R.string.delete),
                icon = Icons.Default.Delete,
                onClick = { showDeleteConfirmDialog = true }
            ),
            secondaryActions = selectionSecondaryActions
        ),
        content = content
    )
}
