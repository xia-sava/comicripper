package to.sava.comicripper.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.window.WindowScope
import to.sava.comicripper.ext.Loader
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.imageio.ImageIO
import javax.swing.SwingUtilities

/**
 * アイコン PNG の Painter を作成する。
 */
@Composable
fun rememberWindowIconPainter(): Painter? = remember {
    Loader.javaClass.getResourceAsStream("/to/sava/comicripper/icon.png")
        ?.use { ImageIO.read(it) }
        ?.toComposeImageBitmap()
        ?.let(::BitmapPainter)
}

/**
 * ウィンドウの初回表示時に最前面化してフォーカスを要求する。
 * Compose Desktop の Window は表示時に isVisible = true を設定するだけで前面化を行なわず、
 * 他のウィンドウ（オーナーウィンドウ等）がフォアグラウンドを持っていると
 * OS が新規ウィンドウのアクティベーションを拒否して背面に出ることがあるため、
 * Window の content 先頭で呼んで明示的に前面化する。
 */
@Composable
fun WindowScope.BringToFrontOnFirstShow() {
    DisposableEffect(window) {
        if (window.isVisible) {
            SwingUtilities.invokeLater { window.forceToFront() }
            onDispose {}
        } else {
            val listener = object : WindowAdapter() {
                override fun windowOpened(e: WindowEvent) {
                    window.removeWindowListener(this)
                    SwingUtilities.invokeLater { window.forceToFront() }
                }
            }
            window.addWindowListener(listener)
            onDispose { window.removeWindowListener(listener) }
        }
    }
}

/**
 * フォーカス移譲が拒否されても Z オーダーの最前面化だけは保証するため、
 * alwaysOnTop を一時的に立てて toFront する。
 */
private fun java.awt.Window.forceToFront() {
    val wasAlwaysOnTop = isAlwaysOnTop
    try {
        isAlwaysOnTop = true
        toFront()
        requestFocusOnContent()
    } finally {
        isAlwaysOnTop = wasAlwaysOnTop
    }
}

/**
 * ウィンドウ内の Compose コンテンツへフォーカスを要求する。
 *
 * Window 自体を focus owner にするとキーイベントが Compose コンテンツへ配送されず、
 * クリックするまでキー操作が効かない。また ComicRipperWindow のオーナー付きダイアログは
 * 表示前に pack() で displayable にするため、Compose 側の自動フォーカス
 * （ComposeWindowPanel.addNotify 時の requestFocus）が不可視ウィンドウ相手に失敗している。
 * そこで表示後に focusable な最深コンポーネント（Compose の描画コンポーネント）を探して
 * 改めてフォーカスを要求する。
 */
private fun java.awt.Window.requestFocusOnContent() {
    (findFocusableLeaf(this) ?: this).requestFocus()
}

private fun findFocusableLeaf(component: java.awt.Component): java.awt.Component? {
    if (!component.isVisible) {
        return null
    }
    if (component is java.awt.Container) {
        component.components.forEach { child ->
            findFocusableLeaf(child)?.let { return it }
        }
    }
    // サイズ 0 のコンポーネントを除外する: Compose のコンテナは focusable だが表示領域を持たない
    // 補助コンポーネント（refocus 対策の InvisibleComponent）を描画コンポーネントより先に
    // 持っており、そこへフォーカスを置くとキーイベントがどこにも配送されない。
    return component.takeIf {
        it !is java.awt.Window && it.isFocusable && it.width > 0 && it.height > 0
    }
}
