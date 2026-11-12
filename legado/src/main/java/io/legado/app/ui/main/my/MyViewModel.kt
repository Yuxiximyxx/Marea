package io.legado.app.ui.main.my

import android.app.Application
import androidx.compose.runtime.Stable
import androidx.lifecycle.viewModelScope
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.EventBus
import io.legado.app.help.LocalNetworkAccess
import io.legado.app.utils.eventBus.FlowEventBus
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Stable
data class MyUiState(
)

sealed class PrefClickEvent {
    data class OpenUrl(val url: String) : PrefClickEvent()
    data class CopyUrl(val url: String) : PrefClickEvent()
    data class StartActivity(val destination: Class<*>, val configTag: String? = null) : PrefClickEvent()
    object OpenReadRecord : PrefClickEvent()
    object OpenBookCacheManage : PrefClickEvent()
    object OpenReplaceRules : PrefClickEvent()
    object OpenHighlightTagRule : PrefClickEvent()
    object OpenTxtTocRule : PrefClickEvent()
    object OpenDictRule : PrefClickEvent()
    object OpenAllBookmark : PrefClickEvent()
    object OpenAbout : PrefClickEvent()
    object ExitApp : PrefClickEvent()
}

sealed interface MyIntent {

    /** 本地网络权限授予后由界面触发，避免再次进入申请分支。 */
}

sealed interface MyEffect {
    /** Android 17 起 Web 服务需要先获得本地网络权限才能被其他设备访问。 */
}

class MyViewModel(
    application: Application
) : BaseViewModel(application) {

    private val _uiState = MutableStateFlow(MyUiState())
    val uiState: StateFlow<MyUiState> = _uiState.asStateFlow()
    private val _effects = MutableSharedFlow<MyEffect>(extraBufferCapacity = 16)
    val effects = _effects.asSharedFlow()

    init {
    }

    fun onIntent(intent: MyIntent) {
        when (intent) {
        }
    }

}
