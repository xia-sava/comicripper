package to.sava.comicripper.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.LocalWindowExceptionHandlerFactory
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.WindowExceptionHandlerFactory
import androidx.compose.ui.window.application
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.awaitCancellation
import java.awt.event.WindowEvent
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities
import kotlin.concurrent.thread

/**
 * JVM 中に1つだけ常駐する Compose Desktop の application スコープを保持し、
 * key を指定して Compose ウィンドウの開閉を行なうためのホスト。
 *
 * application {} スコープは終了させず（exitApplication() を呼ばず）、
 * 個々のウィンドウの表示状態のみを windows リストで管理する。
 * プロセス終了は Main.kt の exitProcess() が担う。
 */
private val logger = KotlinLogging.logger {}

object ComposeWindowHost {
    private class WindowEntry(
        val key: String,
        val content: @Composable (onCloseRequest: () -> Unit) -> Unit,
    )

    private val windows = mutableStateListOf<WindowEntry>()
    private val started = AtomicBoolean(false)

    /**
     * ホストスレッドを起動する。2回目以降の呼び出しは no-op。
     * main() から先行起動しておくと，初回の show() が skiko 初期化待ちにならない。
     *
     * @param onTerminated application {} ブロックの終了（正常・異常問わず）で1度だけ呼ばれる。
     *   ルートウィンドウを Compose に載せた構成では，ここでプロセスの生存管理を終了させる
     *   （このフックが無いと未捕捉例外で application {} が死んだ際に main が待ち続けてゾンビ化する）。
     */
    @OptIn(ExperimentalComposeUiApi::class)
    fun start(onTerminated: (() -> Unit)? = null) {
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
                    // ウィンドウ単位の未捕捉例外を，ログ出力＋当該ウィンドウのクローズに留める。
                    // デフォルトハンドラは再スローするため daemon スレッドごと死に，
                    // 1つのウィンドウの例外がホスト全体（全 Compose 画面）を道連れにしてしまう。
                    CompositionLocalProvider(
                        LocalWindowExceptionHandlerFactory provides windowExceptionHandlerFactory,
                    ) {
                        for (entry in windows) {
                            key(entry) {
                                entry.content { close(entry) }
                            }
                        }
                    }
                }
            } catch (e: Throwable) {
                logger.error(e) { "ComposeWindowHost failed" }
            } finally {
                onTerminated?.invoke()
            }
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    private val windowExceptionHandlerFactory = WindowExceptionHandlerFactory { window ->
        WindowExceptionHandler { throwable ->
            logger.error(throwable) { "window exception" }
            window.dispatchEvent(WindowEvent(window, WindowEvent.WINDOW_CLOSING))
            // 再スローしない: ホスト全体を巻き込まず，例外の発生したウィンドウのみ閉じる。
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
