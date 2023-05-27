package voice.playback.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.view.KeyEvent
import com.squareup.anvil.annotations.ContributesTo
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import voice.common.AppScope
import voice.common.rootComponentAs
import voice.logging.core.Logger
import voice.playback.PlayerController
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

class MediaButtonReceiver : BroadcastReceiver() {

  private val scope = MainScope()

  @Inject
  lateinit var player: PlayerController

  override fun onReceive(context: Context, intent: Intent?) {
    val action = Action.parse(intent)
    Logger.d("onReceive ${intent?.action}. Parsed to $action")
    action ?: return

    rootComponentAs<Component>().inject(this)

    val result = goAsync()
    scope.launch {
      try {
        withTimeout(20.seconds) {
          player.awaitConnect()
          when (action) {
            Action.PlayPause -> player.playPause()
            Action.FastForward -> {
              player.fastForward()
              player.play()
            }

            Action.Rewind -> {
              player.rewind()
              player.play()
            }
          }
        }
      } finally {
        result.finish()
      }
    }
  }

  @ContributesTo(AppScope::class)
  interface Component {
    fun inject(target: MediaButtonReceiver)
  }

  companion object {

    private const val ACTION_KEY = "action"
    private const val WIDGET_ACTION = "voice.WidgetAction"

    fun pendingIntent(
      context: Context,
      action: Action,
    ): PendingIntent? {
      val intent = Intent(WIDGET_ACTION)
        .setComponent(ComponentName(context, MediaButtonReceiver::class.java))
        .putExtra(ACTION_KEY, action.name)
      return PendingIntent.getBroadcast(
        context,
        action.ordinal,
        intent,
        PendingIntent.FLAG_IMMUTABLE,
      )
    }
  }

  enum class Action {
    PlayPause, FastForward, Rewind;

    companion object {
      fun parse(intent: Intent?): Action? {
        return when (intent?.action) {
          Intent.ACTION_MEDIA_BUTTON -> {
            val key: KeyEvent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
              intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
            } else {
              @Suppress("DEPRECATION")
              intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT)
            }
            Logger.d("key=$key")
            if (key?.action == KeyEvent.ACTION_UP) {
              when (key.keyCode) {
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE, KeyEvent.KEYCODE_MEDIA_PLAY -> PlayPause
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD, KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD -> FastForward
                KeyEvent.KEYCODE_MEDIA_REWIND, KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD -> Rewind
                else -> null
              }
            } else {
              null
            }
          }
          WIDGET_ACTION -> {
            entries.find { it.name == intent.getStringExtra(ACTION_KEY) }
          }
          else -> null
        }
      }
    }
  }
}