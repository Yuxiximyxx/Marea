package io.legado.app.ui.widget.components.settingItem

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import io.legado.app.ui.widget.components.SplicedColumnDivider
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.preference.OverlaySpinnerPreference

/**
 * exp.12：MD3 分支已去除，只保留 Miuix 实现（原双引擎中的 miuix 分支）。
 */
@Composable
fun DropdownListSettingItem(
    title: String,
    selectedValue: String,
    displayEntries: Array<String>,
    entryValues: Array<String>,
    description: String? = null,
    imageVector: ImageVector? = null,
    onValueChange: (String) -> Unit
) {
    SplicedColumnDivider()

    val selectedIndex = entryValues.indexOf(selectedValue).coerceAtLeast(0)
    val spinnerItems = displayEntries.map { display ->
        DropdownItem(title = display)
    }

    OverlaySpinnerPreference(
        title = title,
        summary = description,
        items = spinnerItems,
        selectedIndex = selectedIndex,
        startAction = imageVector?.let { icon ->
            {
                Icon(
                    imageVector = icon,
                    contentDescription = null
                )
            }
        },
        onSelectedIndexChange = { index ->
            onValueChange(entryValues[index])
        }
    )
}
