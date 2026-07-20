package to.sava.comicripper.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val ERROR_TOAST_DURATION_MS = 4_000L

/**
 * 操作失敗を知らせるトーストの表示状態。
 * show() でメッセージを表示すると、一定時間後に自動で消える。
 */
@Stable
class ErrorToastState {
    var message by mutableStateOf<String?>(null)
        private set

    fun show(message: String) {
        this.message = message
    }

    fun dismiss() {
        message = null
    }
}

@Composable
fun rememberErrorToastState(): ErrorToastState = remember { ErrorToastState() }

/**
 * ウィンドウ内に被せる失敗通知トースト。
 * ウィンドウコンテンツのルート Box 内で、他のオーバーレイと同様に最前面へ置くこと。
 */
@Composable
fun ErrorToast(state: ErrorToastState) {
    val message = state.message ?: return
    LaunchedEffect(message) {
        delay(ERROR_TOAST_DURATION_MS)
        state.dismiss()
    }
    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = MaterialTheme.shapes.medium,
            shadowElevation = 4.dp,
        ) {
            Text(
                message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
    }
}
