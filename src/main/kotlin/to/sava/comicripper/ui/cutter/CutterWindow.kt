package to.sava.comicripper.ui.cutter

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import to.sava.comicripper.Main
import to.sava.comicripper.model.Comic
import to.sava.comicripper.model.Setting
import to.sava.comicripper.repository.ComicRepository
import to.sava.comicripper.ui.ComposeWindowHost
import to.sava.comicripper.ui.detail.showDetailWindow
import to.sava.comicripper.ui.rememberWindowIconPainter
import kotlin.math.min

private const val WINDOW_TITLE = "comicripper ${Main.VERSION}"

/** キー操作1回あたりのガイド移動量（%） */
private const val CUTTER_KEY_STEP = 0.1

/**
 * 切り出し処理の実行スコープ。
 * ウィンドウを閉じた後も cutCover() のファイル書き込みを完走させるため、
 * composition のライフサイクルから独立させる。
 */
private val cutterScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * Cutter ウィンドウを開く。
 * Comic ごとに1枚まで（同一 Comic で既に開いていれば何もしない）。
 * 異なる Comic の Cutter は同時に複数開ける。
 */
fun showCutterWindow(comic: Comic) {
    ComposeWindowHost.show(key = "cutter:${comic.id}") { onCloseRequest ->
        CutterWindow(comic = comic, onCloseRequest = onCloseRequest)
    }
}

/**
 * 表紙切り出し画面。
 * coverF 画像の上に左右の切り出しガイド線を重ね、スライダーとキー操作で位置を調整して
 * cutCover() で coverA を生成する。ガイド線は表示画像の幅に対する percent 位置そのものに
 * 描画され、切り出し結果と画面表示が一致する。
 */
@Composable
fun CutterWindow(comic: Comic, onCloseRequest: () -> Unit) {
    val state = rememberWindowState(
        size = DpSize(Setting.cutterWindowWidth.dp, Setting.cutterWindowHeight.dp),
        position = if (Setting.cutterWindowPosX >= 0.0) {
            WindowPosition.Absolute(Setting.cutterWindowPosX.dp, Setting.cutterWindowPosY.dp)
        } else {
            WindowPosition.PlatformDefault
        },
    )
    LaunchedEffect(state) {
        snapshotFlow { state.size }.collect { size ->
            Setting.cutterWindowWidth = size.width.value.toDouble()
            Setting.cutterWindowHeight = size.height.value.toDouble()
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.position }.collect { position ->
            if (position is WindowPosition.Absolute) {
                Setting.cutterWindowPosX = position.x.value.toDouble()
                Setting.cutterWindowPosY = position.y.value.toDouble()
            }
        }
    }

    var leftPercent by remember { mutableStateOf(Setting.cutterLeftPercent) }
    var rightPercent by remember { mutableStateOf(Setting.cutterRightPercent) }

    fun updateLeft(value: Double) {
        val clamped = value.coerceIn(0.0, 100.0)
        leftPercent = clamped
        Setting.cutterLeftPercent = clamped
    }

    fun updateRight(value: Double) {
        val clamped = value.coerceIn(0.0, 100.0)
        rightPercent = clamped
        Setting.cutterRightPercent = clamped
    }

    var coverImage by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(comic) {
        coverImage = withContext(Dispatchers.IO) {
            runCatching { comic.coverFullImage?.toComposeImageBitmap() }
                .onFailure { println("cutter image load failed: ${it.message}") }
                .getOrNull()
        }
    }

    // スライダーの表示幅・位置を、実際に表示されている画像の矩形（drawCutterGuidesと同じ計算）に
    // 追従させる。画像が表示領域いっぱいに広がらない場合でも、スライダーの操作感とガイド線の
    // 位置が一致するようにするため。
    var imageBoxSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val (imageLeftDp, imageWidthDp) = remember(imageBoxSize, coverImage, density) {
        val bitmap = coverImage
        if (bitmap == null || imageBoxSize.width <= 0 || imageBoxSize.height <= 0) {
            with(density) { 0.dp to imageBoxSize.width.toFloat().toDp() }
        } else {
            val rect = imageDisplayRect(imageBoxSize.width.toFloat(), imageBoxSize.height.toFloat(), bitmap)
            with(density) { rect.left.toDp() to rect.width.toDp() }
        }
    }

    val repos = remember { ComicRepository() }

    fun openDetail() {
        onCloseRequest()
        showDetailWindow(comic)
    }

    fun cut() {
        val left = leftPercent
        val right = rightPercent
        cutterScope.launch {
            runCatching { repos.cutCover(comic, left, right, 0.0) }
                .onFailure { println("cutCover failed: ${it.message}") }
        }
        openDetail()
    }

    Window(
        onCloseRequest = onCloseRequest,
        state = state,
        title = "${comic.title} ${comic.author} - $WINDOW_TITLE",
        icon = rememberWindowIconPainter(),
        onPreviewKeyEvent = { event ->
            if (event.type != KeyEventType.KeyDown) {
                false
            } else {
                when (event.key) {
                    Key.Escape -> {
                        onCloseRequest()
                        true
                    }
                    Key.Enter, Key.NumPadEnter -> {
                        cut()
                        true
                    }
                    Key.DirectionLeft -> {
                        when (event.isShiftPressed) {
                            true -> updateRight(rightPercent - CUTTER_KEY_STEP)
                            false -> updateLeft(leftPercent - CUTTER_KEY_STEP)
                        }
                        true
                    }
                    Key.DirectionRight -> {
                        when (event.isShiftPressed) {
                            true -> updateRight(rightPercent + CUTTER_KEY_STEP)
                            false -> updateLeft(leftPercent + CUTTER_KEY_STEP)
                        }
                        true
                    }
                    else -> false
                }
            }
        },
    ) {
        MaterialTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .width(imageWidthDp)
                                .offset(x = imageLeftDp),
                        ) {
                            Slider(
                                value = leftPercent.toFloat(),
                                onValueChange = { updateLeft(it.toDouble()) },
                                valueRange = 0f..100f,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Slider(
                                value = rightPercent.toFloat(),
                                onValueChange = { updateRight(it.toDouble()) },
                                valueRange = 0f..100f,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1.0f)
                            .onSizeChanged { imageBoxSize = it },
                    ) {
                        coverImage?.let { bitmap ->
                            Image(
                                bitmap = bitmap,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize(),
                            )
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawCutterGuides(bitmap, leftPercent, rightPercent)
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Button(onClick = { cut() }, modifier = Modifier.width(150.dp)) {
                            Text("決定")
                        }
                        Button(onClick = onCloseRequest, modifier = Modifier.width(150.dp)) {
                            Text("キャンセル")
                        }
                        if (comic.coverAlbum == null) {
                            VerticalDivider(modifier = Modifier.height(24.dp))
                            Button(onClick = { openDetail() }, modifier = Modifier.width(150.dp)) {
                                Text("詳細画面へ")
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val GUIDE_TRIANGLE_WIDTH = 10f
private const val GUIDE_TRIANGLE_HEIGHT = 30f

/**
 * ContentScale.Fit と同じ計算で、コンテナ内に実際に表示される画像の矩形を求める。
 * ガイド線描画とスライダーの表示位置合わせの両方で使う。
 */
private fun imageDisplayRect(containerWidth: Float, containerHeight: Float, bitmap: ImageBitmap): Rect {
    val scale = min(containerWidth / bitmap.width, containerHeight / bitmap.height)
    val imageWidth = bitmap.width * scale
    val imageHeight = bitmap.height * scale
    val left = (containerWidth - imageWidth) / 2f
    val top = (containerHeight - imageHeight) / 2f
    return Rect(left, top, left + imageWidth, top + imageHeight)
}

/**
 * 左右の切り出しガイド線を描画する。
 * 表示画像矩形の幅に対する percent 位置に縦線を引く。
 */
private fun DrawScope.drawCutterGuides(
    bitmap: ImageBitmap,
    leftPercent: Double,
    rightPercent: Double,
) {
    val imageRect = imageDisplayRect(size.width, size.height, bitmap)
    val leftX = imageRect.left + (imageRect.width * leftPercent / 100.0).toFloat()
    val rightX = imageRect.left + (imageRect.width * rightPercent / 100.0).toFloat()
    drawGuideLine(leftX, imageRect.top, imageRect.bottom, triangleTowardRight = true)
    drawGuideLine(rightX, imageRect.top, imageRect.bottom, triangleTowardRight = false)
}

/**
 * 1本のガイド線を描画する。
 * x が切り出し位置。x を挟んで黒・白の縦破線を隣接して引き（どんな背景でも視認できる二重破線）、
 * 上端に黒塗り・白縁の三角形マーカーを付ける。
 */
private fun DrawScope.drawGuideLine(
    x: Float,
    top: Float,
    bottom: Float,
    triangleTowardRight: Boolean,
) {
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))
    drawLine(
        color = Color.Black,
        start = Offset(x - 0.5f, top),
        end = Offset(x - 0.5f, bottom),
        strokeWidth = 1f,
        pathEffect = dashEffect,
    )
    drawLine(
        color = Color.White,
        start = Offset(x + 0.5f, top),
        end = Offset(x + 0.5f, bottom),
        strokeWidth = 1f,
        pathEffect = dashEffect,
    )

    val apexX = when (triangleTowardRight) {
        true -> x + GUIDE_TRIANGLE_WIDTH
        false -> x - GUIDE_TRIANGLE_WIDTH
    }
    val triangle = Path().apply {
        moveTo(x, top)
        lineTo(x, top + GUIDE_TRIANGLE_HEIGHT)
        lineTo(apexX, top + GUIDE_TRIANGLE_HEIGHT / 2f)
        close()
    }
    drawPath(triangle, Color.Black)
    drawPath(triangle, Color.White, style = Stroke(width = 1f))
}
