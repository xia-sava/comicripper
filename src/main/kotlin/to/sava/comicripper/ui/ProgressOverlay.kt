package to.sava.comicripper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import to.sava.comicripper.application.ApplicationScope

private val logger = KotlinLogging.logger {}

/**
 * ウィンドウ内に被せる進捗オーバーレイの状態。
 * launchTask() でタスクを開始すると表示され、完了（成功・失敗とも）で消える。
 *
 * @param scope block を実行するスコープ。呼び出し元ウィンドウが閉じても完走させるため、
 *   composition のライフサイクルから独立した [ApplicationScope] を渡すこと。
 * @param onError block が例外を投げた際に呼ばれる。ユーザーへの失敗通知（[ErrorToastState]等）に使う。
 */
@Stable
class ProgressOverlayState(
    private val scope: CoroutineScope,
    private val onError: (title: String) -> Unit = {},
) {
    class Task(val title: String, val text: String)

    var task by mutableStateOf<Task?>(null)
        private set

    val isActive: Boolean get() = task != null

    /**
     * タスクを開始してオーバーレイを表示する。実行中は多重起動しない。
     */
    fun launchTask(title: String, text: String, block: suspend CoroutineScope.() -> Unit) {
        if (isActive) {
            return
        }
        task = Task(title, text)
        scope.launch {
            try {
                block()
            } catch (e: Exception) {
                logger.warn(e) { "$title failed" }
                onError(title)
            } finally {
                task = null
            }
        }
    }
}

@Composable
fun rememberProgressOverlayState(onError: (title: String) -> Unit = {}): ProgressOverlayState {
    val scope: ApplicationScope = koinInject()
    return remember { ProgressOverlayState(scope, onError) }
}

/**
 * 半透明の背景で下の UI へのマウス操作を遮断し、タイトル・本文・
 * ProgressIndicator を中央に表示するオーバーレイ。
 * ウィンドウコンテンツのルート Box 内で、通常コンテンツの後（最前面）に置くこと。
 * キーボード操作の遮断は呼び出し側の onPreviewKeyEvent で isActive を見て行なうこと。
 */
@Composable
fun ProgressOverlay(state: ProgressOverlayState) {
    val task = state.task ?: return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f))
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            shadowElevation = 4.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(task.title, style = MaterialTheme.typography.titleMedium)
                Text(task.text)
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
            }
        }
    }
}
