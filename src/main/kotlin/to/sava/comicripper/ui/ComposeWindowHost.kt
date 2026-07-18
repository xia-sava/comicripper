package to.sava.comicripper.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.window.application
import kotlinx.coroutines.awaitCancellation
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

/**
 * JVM 中に1つだけ常駐する Compose Desktop の application スコープを保持し、
 * JavaFX 側から Compose ウィンドウの開閉を行なうためのホスト。
 *
 * application {} スコープは終了させず（exitApplication() を呼ばず）、
 * 個々のウィンドウの表示状態のみを windows リストで管理する。
 * プロセス終了は Main.stop() の exitProcess() が担う。
 */
object ComposeWindowHost {
    private class WindowEntry(
        val key: String,
        val content: @Composable (onCloseRequest: () -> Unit) -> Unit,
    )

    private val windows = mutableStateListOf<WindowEntry>()
    private val started = AtomicBoolean(false)

    /**
     * ホストスレッドを起動する。2回目以降の呼び出しは no-op。
     * Main.start() から先行起動しておくと，初回の show() が skiko 初期化待ちにならない。
     */
    fun start() {
        if (!started.compareAndSet(false, true)) {
            return
        }
        thread(name = "ComposeWindowHost", isDaemon = true) {
            try {
                application(exitProcessOnExit = false) {
                    // Compose の application はアクティブな composition（Window または
                    // 実行中の effect）が無くなると終了するため，ウィンドウ 0 個でも
                    // ホストを維持する keep-alive として常駐コルーチンを置く．
                    LaunchedEffect(Unit) {
                        awaitCancellation()
                    }
                    for (entry in windows) {
                        key(entry) {
                            entry.content { close(entry) }
                        }
                    }
                }
            } catch (e: Throwable) {
                System.err.println("ComposeWindowHost failed: ${e.javaClass.name}: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    /**
     * Compose ウィンドウを開く。同じ key のウィンドウが開いている間は no-op。
     * content には閉じるためのコールバックが渡されるので，
     * Window(onCloseRequest = ...) と閉じるボタンの両方に配線すること。
     */
    fun show(key: String, content: @Composable (onCloseRequest: () -> Unit) -> Unit) {
        start()
        SwingUtilities.invokeLater {
            if (windows.none { it.key == key }) {
                windows.add(WindowEntry(key, content))
            }
        }
    }

    private fun close(entry: WindowEntry) {
        SwingUtilities.invokeLater {
            windows.remove(entry)
        }
    }
}
