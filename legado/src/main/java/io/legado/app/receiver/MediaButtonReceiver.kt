package io.legado.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.KeyEvent
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.domain.gateway.ReadAloudSettingsGateway
import io.legado.app.help.LifecycleHelp
import io.legado.app.model.ReadBook
import io.legado.app.ui.main.MainActivity
import io.legado.app.utils.LogUtils
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.postEvent
import org.koin.core.context.GlobalContext


/**
 * Created by GKF on 2018/1/6.
 * 监听耳机键
 */
class MediaButtonReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (handleIntent(context, intent) && isOrderedBroadcast) {
            abortBroadcast()
        }
    }

    companion object {

        private const val TAG = "MediaButtonReceiver"

        private val aloudSettingsGateway get() = GlobalContext.get().get<ReadAloudSettingsGateway>()

        fun handleIntent(context: Context, intent: Intent): Boolean {
            val intentAction = intent.action
            if (Intent.ACTION_MEDIA_BUTTON == intentAction) {
                @Suppress("DEPRECATION")
                val keyEvent = intent.getParcelableExtra<KeyEvent>(Intent.EXTRA_KEY_EVENT)
                    ?: return false
                val keycode: Int = keyEvent.keyCode
                val action: Int = keyEvent.action
                if (action == KeyEvent.ACTION_DOWN) {
                    LogUtils.d(TAG, "Receive mediaButton event, keycode:$keycode")
                    when (keycode) {
                        KeyEvent.KEYCODE_MEDIA_PREVIOUS -> {
                            if (context.getPrefBoolean(PreferKey.mediaButtonPerNext, false)) {
                                ReadBook.moveToPrevChapter(true)
                            } else {
                                Unit
                            }
                        }

                        KeyEvent.KEYCODE_MEDIA_NEXT -> {
                            if (context.getPrefBoolean(PreferKey.mediaButtonPerNext, false)) {
                                ReadBook.moveToNextChapter(true)
                            } else {
                                Unit
                            }
                        }

                        else -> readAloud(context)
                    }
                }
            }
            return true
        }

        fun readAloud(context: Context, isMediaKey: Boolean = true) {
            when {
                isMediaKey && !aloudSettingsGateway.currentSettings.readAloudByMediaButton -> {
                    // break
                }

                MainActivity.hasActiveReadBookRoute ->
                    postEvent(EventBus.MEDIA_BUTTON, true)

                else -> Unit
            }
        }
    }

}
