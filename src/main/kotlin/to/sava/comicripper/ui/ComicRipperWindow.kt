package to.sava.comicripper.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.awt.ComposeDialog
import androidx.compose.ui.awt.SwingDialog
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import java.awt.Dialog.ModalityType
import java.awt.Dimension
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.ComponentListener
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.event.WindowListener
import javax.swing.WindowConstants
import kotlin.math.roundToInt

/**
 * アプリ画面用の共通ウィンドウ。owner の有無で実体を切り替える。
 *
 * - owner が null: 通常のトップレベル [Window]。
 * - owner が非 null: ComposeDialog(owner, MODELESS) による非モーダルのオーナー付きダイアログ。
 *   Windows では owned ウィンドウの Z オーダーが常にオーナーより上であることを OS が保証するため、
 *   フォーカスがオーナー側へ移っても owned ウィンドウが背後に回らない。
 *
 * content の receiver は [WindowScope]。content 内では window プロパティで自ウィンドウの
 * java.awt.Window を参照でき、そこから開く画面の owner として渡せる
 * （WindowScope.window は java.awt.Window 型であり、ComposeWindow/ComposeDialog 固有 API には
 * 依存しないこと）。
 *
 * オーナー付きの場合、state の placement / isMinimized は反映しない
 * （AWT Dialog は最大化・最小化ボタンを持たず、タスクバーにも表示されない）。
 */
@Composable
fun ComicRipperWindow(
    onCloseRequest: () -> Unit,
    state: WindowState,
    title: String,
    icon: Painter? = null,
    owner: java.awt.Window? = null,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    content: @Composable WindowScope.() -> Unit,
) {
    if (owner == null) {
        // 高レベル Window も内部の SwingWindow が onPreviewKeyEvent を生成時の1回しか
        // 束縛しないため、rememberUpdatedState を挟んで再コンポジション後も
        // ハンドラが最新のクロージャを参照するようにする（OwnedDialogWindow と同じ対策）。
        val currentOnPreviewKeyEvent by rememberUpdatedState(onPreviewKeyEvent)
        Window(
            onCloseRequest = onCloseRequest,
            state = state,
            title = title,
            icon = icon,
            onPreviewKeyEvent = { event -> currentOnPreviewKeyEvent(event) },
        ) {
            content()
        }
    } else {
        OwnedDialogWindow(
            owner = owner,
            onCloseRequest = onCloseRequest,
            state = state,
            title = title,
            icon = icon,
            onPreviewKeyEvent = onPreviewKeyEvent,
            content = content,
        )
    }
}

/**
 * オーナー付き非モーダルダイアログ。
 * 高レベル [Window] が内部で行なっている title / icon / size / position の双方向同期のうち、
 * この用途に必要な最小限を低レベル [DialogWindow] の update / create で再実装している
 * （高レベル版の同期ユーティリティは internal で流用できない）。
 *
 * onCloseRequest は同一ウィンドウに対して複数回呼ばれることがある
 * （×ボタンとオーナー閉鎖カスケードの競合時）。呼び出し側（ComposeWindowHost.close）は
 * windows.remove(entry) で冪等なのでこの契約を満たす。
 */
@Composable
private fun OwnedDialogWindow(
    owner: java.awt.Window,
    onCloseRequest: () -> Unit,
    state: WindowState,
    title: String,
    icon: Painter?,
    onPreviewKeyEvent: (KeyEvent) -> Boolean,
    content: @Composable WindowScope.() -> Unit,
) {
    val currentState by rememberUpdatedState(state)
    val currentTitle by rememberUpdatedState(title)
    val currentIcon by rememberUpdatedState(icon)
    val currentOnCloseRequest by rememberUpdatedState(onCloseRequest)
    // 低レベル DialogWindow(create=...) は create ブロック内の setContent() で
    // onPreviewKeyEvent を最初の1回しか束縛しないため、rememberUpdatedState を挟んで
    // 生成後の再コンポジションでもハンドラが最新のクロージャを参照するようにする
    // （でないと Ctrl+D 等のキー操作がウィンドウを開いた時点の state を掴んだまま固まる）。
    val currentOnPreviewKeyEvent by rememberUpdatedState(onPreviewKeyEvent)
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    // ダイアログへ適用済みの値。state 変更とネイティブ側イベントの競合を避けるための
    // 高レベル Window 実装と同じパターン。
    val appliedState = remember {
        object {
            var size: DpSize? = null
            var position: WindowPosition? = null
            var title: String? = null
            var icon: Painter? = null
        }
    }
    val listeners = remember {
        object {
            var windowListener: WindowListener? = null
            var componentListener: ComponentListener? = null

            fun removeFrom(dialog: ComposeDialog) {
                windowListener?.let(dialog::removeWindowListener)
                componentListener?.let(dialog::removeComponentListener)
                windowListener = null
                componentListener = null
            }
        }
    }

    SwingDialog(
        onPreviewKeyEvent = { event -> currentOnPreviewKeyEvent(event) },
        create = {
            ComposeDialog(
                owner = owner,
                modalityType = ModalityType.MODELESS,
                graphicsConfiguration = owner.graphicsConfiguration,
            ).apply {
                // 閉じる操作は onCloseRequest 経由で composition から降ろすことで反映する
                defaultCloseOperation = WindowConstants.DO_NOTHING_ON_CLOSE
                object : WindowAdapter() {
                    override fun windowClosing(e: WindowEvent) {
                        currentOnCloseRequest()
                    }

                    override fun windowClosed(e: WindowEvent) {
                        // オーナーの dispose() 連鎖でこのダイアログが破棄された場合にも
                        // ComposeWindowHost 側のエントリを除去する（通常の閉じ経路では
                        // dispose 前にリスナーを外すためここは呼ばれない）。
                        // このリスナーを削除しないこと: 無いとオーナー閉鎖時にゾンビエントリが
                        // 残り、同じkeyのウィンドウが二度と開けなくなる。
                        currentOnCloseRequest()
                    }
                }.also {
                    addWindowListener(it)
                    listeners.windowListener = it
                }
                object : ComponentAdapter() {
                    override fun componentResized(e: ComponentEvent) {
                        currentState.size = DpSize(width.dp, height.dp)
                        appliedState.size = currentState.size
                    }

                    override fun componentMoved(e: ComponentEvent) {
                        currentState.position = WindowPosition(x.dp, y.dp)
                        appliedState.position = currentState.position
                    }
                }.also {
                    addComponentListener(it)
                    listeners.componentListener = it
                }
            }
        },
        dispose = { dialog ->
            // dispose() 後も AWT はリスナーを呼びうるので先に外す
            listeners.removeFrom(dialog)
            dialog.dispose()
        },
        update = { dialog ->
            if (currentTitle != appliedState.title) {
                dialog.title = currentTitle
                appliedState.title = currentTitle
            }
            if (currentIcon != appliedState.icon) {
                dialog.setIconImage(
                    currentIcon?.toAwtImage(density, layoutDirection, Size(192f, 192f)),
                )
                appliedState.icon = currentIcon
            }
            if (currentState.size != appliedState.size) {
                dialog.applySize(currentState.size)
                appliedState.size = currentState.size
            }
            if (currentState.position != appliedState.position) {
                dialog.applyPosition(currentState.position, owner)
                appliedState.position = currentState.position
            }
        },
    ) {
        content()
    }
}

/**
 * state.size をダイアログへ反映する。
 * 未表示のうちに pack() で displayable にしておくと、DialogWindow が表示前に初回フレームを
 * 描画するため、表示直後に背景色だけのウィンドウが見えることがない。
 * 幅・高さのどちらかが Unspecified な場合は pack() 後のサイズをそのまま使う
 * （DpSize.isSpecified は両次元とも Unspecified の特殊値かどうかしか見ないため、
 * 片方だけ Unspecified だと roundToInt() が NaN で例外を投げる。現状の呼び出し元は
 * 両次元とも指定済みのため実害は無いが、防御的に次元ごとに判定する）。
 */
private fun ComposeDialog.applySize(size: DpSize) {
    val isWidthSpecified = size.isSpecified && size.width.isSpecified
    val isHeightSpecified = size.isSpecified && size.height.isSpecified
    if (!isWidthSpecified || !isHeightSpecified) {
        if (!isDisplayable) {
            pack()
        }
        if (isWidthSpecified || isHeightSpecified) {
            val packed = this.size
            setSize(
                if (isWidthSpecified) size.width.value.roundToInt().coerceAtLeast(0) else packed.width,
                if (isHeightSpecified) size.height.value.roundToInt().coerceAtLeast(0) else packed.height,
            )
        }
        return
    }
    val width = size.width.value.roundToInt().coerceAtLeast(0)
    val height = size.height.value.roundToInt().coerceAtLeast(0)
    if (!isDisplayable) {
        preferredSize = Dimension(width, height)
        pack()
    }
    setSize(width, height)
}

/**
 * state.position をダイアログへ反映する。
 * Absolute 以外（PlatformDefault 等）はオーナー中央に配置する。
 */
private fun ComposeDialog.applyPosition(position: WindowPosition, owner: java.awt.Window) {
    when (position) {
        is WindowPosition.Absolute ->
            setLocation(position.x.value.roundToInt(), position.y.value.roundToInt())
        else -> setLocationRelativeTo(owner)
    }
}
