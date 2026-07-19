package to.sava.comicripper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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

/**
 * テキストエリアオーバーレイの表示状態。
 * show() で表示、完了/キャンセルどちらの操作でも dismiss() で消える。
 */
@Stable
class TextAreaOverlayState {
    class Request(
        val title: String,
        val prompt: String,
        val initialText: String,
        val onResult: (String) -> Unit,
    )

    var request by mutableStateOf<Request?>(null)
        private set

    val isActive: Boolean get() = request != null

    /**
     * オーバーレイを表示する。実行中は多重表示しない。
     * onResult は「完了」操作時、text を引数にダイアログを閉じた後に呼ばれる。
     */
    fun show(title: String, prompt: String, text: String, onResult: (String) -> Unit) {
        if (isActive) {
            return
        }
        request = Request(title, prompt, text, onResult)
    }

    fun dismiss() {
        request = null
    }
}

@Composable
fun rememberTextAreaOverlayState(): TextAreaOverlayState = remember { TextAreaOverlayState() }

/**
 * 半透明の背景で下の UI へのマウス操作を遮断し、タイトル・プロンプト・
 * 複数行テキストフィールド・完了/キャンセルボタンを中央に表示するオーバーレイ。
 * ウィンドウコンテンツのルート Box 内で、通常コンテンツの後（最前面）に置くこと。
 * キーボード操作の遮断（矢印/Enterを奪わない）は呼び出し側の onPreviewKeyEvent で
 * isActive を見て行なうこと。
 */
@Composable
fun TextAreaOverlay(state: TextAreaOverlayState) {
    val request = state.request ?: return
    var text by remember(request) { mutableStateOf(request.initialText) }

    fun complete() {
        val result = text
        state.dismiss()
        // コールバック内の例外も ComposeWindowHost 全体を道連れにするため保護する。
        runCatching { request.onResult(result) }
            .onFailure { println("TextAreaOverlay onResult failed: ${it.javaClass.simpleName}: ${it.message}") }
    }

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
                modifier = Modifier.padding(24.dp).width(480.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(request.title, style = MaterialTheme.typography.titleMedium)
                Text(request.prompt)
                CompactOutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth().height(320.dp),
                )
                Row(
                    modifier = Modifier.align(Alignment.End),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CompactButton(onClick = { state.dismiss() }) { Text("キャンセル") }
                    CompactButton(onClick = { complete() }) { Text("完了") }
                }
            }
        }
    }
}
