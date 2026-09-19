package to.sava.comicripper.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowScope
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.flow.drop
import to.sava.comicripper.ext.Loader
import to.sava.comicripper.model.WindowGeometry
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import javax.swing.SwingUtilities

/**
 * 設定に保存されたサイズ・位置でウィンドウ状態を作り、以降の変更を設定へ書き戻す。
 * 位置が未設定なら配置をプラットフォームへ任せる。
 *
 * 初期値の読み出しはスナップショットの購読から外す。購読してしまうと、書き戻しのたびに
 * 呼び出し元のコンポジションが無効化され、ウィンドウを動かすだけで画面全体が再コンポーズされる。
 */
@Composable
fun rememberPersistedWindowState(geometry: WindowGeometry): WindowState {
    val initialSize = remember {
        Snapshot.withoutReadObservation { DpSize(geometry.width.dp, geometry.height.dp) }
    }
    val initialPosition = remember {
        Snapshot.withoutReadObservation {
            if (geometry.posX >= 0.0) {
                WindowPosition.Absolute(geometry.posX.dp, geometry.posY.dp)
            } else {
                WindowPosition.PlatformDefault
            }
        }
    }
    val state = rememberWindowState(size = initialSize, position = initialPosition)
    LaunchedEffect(state) {
        snapshotFlow { state.size }.collect { size ->
            geometry.width = size.width.value.toDouble()
            geometry.height = size.height.value.toDouble()
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.position }.collect { position ->
            if (position is WindowPosition.Absolute) {
                geometry.posX = position.x.value.toDouble()
                geometry.posY = position.y.value.toDouble()
            }
        }
    }
    return state
}

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
 * 作業ディレクトリの画像を Compose で表示する形へ変換する。
 *
 * ImageIO は 8bit グレースケールの画像を、ガンマなしのグレー色空間を持つ TYPE_BYTE_GRAY として読む。
 * 実際の値はガンマ付きなので、getRGB で sRGB へ変換する [toComposeImageBitmap] に直接渡すと
 * 中間調が持ち上がって薄く表示される。Java2D の描画はグレーの値をそのまま RGB へ写すため、
 * 先に TYPE_INT_RGB へ描き直す。
 */
fun BufferedImage.toDisplayImageBitmap(): ImageBitmap =
    (if (type == BufferedImage.TYPE_BYTE_GRAY) toIntRgb() else this).toComposeImageBitmap()

private fun BufferedImage.toIntRgb(): BufferedImage =
    BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).also { rgb ->
        rgb.createGraphics().apply {
            drawImage(this@toIntRgb, 0, 0, null)
            dispose()
        }
    }

/**
 * ウィンドウの初回表示時と、[ComposeWindowHost.show] で同じウィンドウが再び求められたときに、
 * 最前面化してフォーカスを要求する。
 * Compose Desktop の Window は表示時に isVisible = true を設定するだけで前面化を行なわず、
 * 他のウィンドウ（オーナーウィンドウ等）がフォアグラウンドを持っていると
 * OS が新規ウィンドウのアクティベーションを拒否して背面に出ることがあるため、
 * Window の content 先頭で呼んで明示的に前面化する。
 */
@Composable
fun WindowScope.BringToFrontOnShow() {
    val requests = LocalBringToFrontRequests.current
    LaunchedEffect(window, requests) {
        snapshotFlow { requests.intValue }.drop(1).collect { window.forceToFront() }
    }
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
