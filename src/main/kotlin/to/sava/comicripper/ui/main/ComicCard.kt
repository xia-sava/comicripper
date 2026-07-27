package to.sava.comicripper.ui.main

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.onClick
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.github.oshai.kotlinlogging.KotlinLogging
import to.sava.comicripper.domain.model.Comic
import java.awt.image.BufferedImage
import kotlin.math.roundToInt

private val logger = KotlinLogging.logger {}

/** 選択時の背景（antiquewhite）と枠（黒）。common.css の .comic.selected に対応。 */
private val SelectedBackground = Color(0xFFFAEBD7)
private val SelectedBorder = Color.Black

/** 未選択時の背景（白）と枠（silver）。common.css の .comic に対応。 */
private val UnselectedBackground = Color.White
private val UnselectedBorder = Color(0xFFC0C0C0)

/** ドラッグ元の枠（青）とドロップ先候補の枠（赤）。common.css の .comic.dragged/.dragover に対応。 */
private val DraggedBorder = Color.Blue
private val DropTargetBorder = Color.Red

private const val MAX_AUTHOR_LENGTH = 20
private const val MAX_TITLE_LENGTH = 40

/** 表紙サムネイルの最大表示サイズ（1枚目）。 */
private const val COVER_FIT_WIDTH = 256f
private const val COVER_FIT_HEIGHT = 128f

/** 2枚目以降のサムネイルの最大表示サイズと重ね描きのずらし量。 */
private const val PAGE_FIT_SIZE = 128f
private const val PAGE_OVERLAP_STEP = 3f

/**
 * カードに表示するサムネイル。表紙と、2枚目以降を1枚へ合成した帯を持つ。
 * [pageStrip] は2枚目以降が無い場合 null。
 */
private class CardThumbnails(val cover: ImageBitmap, val pageStrip: ImageBitmap?)

/**
 * コミック1件を表すカード。
 * 著者名・題名と、表紙サムネイル＋2枚目以降の重ね描きを表示する。
 * クリックで選択、ダブルクリックで詳細/カット画面を開く。
 * ドラッグで別カードへ重ねると、そのカードへの Comic マージを起こす。
 *
 * @param isDragged 自身がドラッグ元のとき true（枠を青にする）。
 * @param isDropTarget 自身がドロップ先候補のとき true（枠を赤にする）。
 * @param onBoundsInParent FlowRow 座標系での自身の矩形を親へ通知する
 *   （選択カードのスクロール補正に使う。スクロール位置に依存しない座標）。
 * @param onMerge ドロップ確定時に (ドラッグ元 id, ドロップ先 id) を通知する。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ComicCard(
    comic: Comic,
    selected: Boolean,
    isDragged: Boolean,
    isDropTarget: Boolean,
    dragState: ComicDragState,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
    onBoundsInParent: (Rect) -> Unit,
    onMerge: (srcId: String, dstId: String) -> Unit,
) {
    // changeFlow の発火ごとにインクリメントし、著者名/題名/サムネイルの再計算キーにする。
    var version by remember { mutableStateOf(0) }
    LaunchedEffect(comic) {
        comic.changeFlow.collect { version++ }
    }

    val author = remember(comic, version) { truncateForDisplay(comic.author, MAX_AUTHOR_LENGTH) }
    val title = remember(comic, version) { truncateForDisplay(comic.title, MAX_TITLE_LENGTH) }

    // BufferedImage → ImageBitmap 変換と帯の合成は重いので remember でキャッシュする
    // （非 Lazy リストで全カードが同時に compose されるため、毎回変換すると全カード分走る）。
    // 画像変換系の例外はホスト全体を道連れにするため runCatching で保護する。
    val density = LocalDensity.current
    val thumbnails = remember(comic, version, density) {
        runCatching { buildCardThumbnails(comic.thumbnails, density) }
            .onFailure { logger.warn(it) { "thumbnail convert failed" } }
            .getOrNull()
    }

    // カード破棄時にドラッグ状態から確実に除去する（stale bounds による誤ヒット防止）。
    DisposableEffect(comic.id) {
        onDispose { dragState.unregister(comic.id) }
    }

    val borderColor = when {
        isDragged -> DraggedBorder
        isDropTarget -> DropTargetBorder
        selected -> SelectedBorder
        else -> UnselectedBorder
    }

    Column(
        modifier = Modifier
            .onGloballyPositioned {
                onBoundsInParent(it.boundsInParent())
                dragState.register(comic.id, it.positionInWindow(), it.boundsInWindow())
            }
            .background(if (selected) SelectedBackground else UnselectedBackground)
            .border(1.dp, borderColor)
            .onClick(onDoubleClick = onOpen, onClick = onSelect)
            .pointerInput(comic.id) {
                detectDragGestures(
                    onDragStart = { offset ->
                        dragState.start(comic.id)
                        dragState.drag(offset)
                    },
                    onDrag = { change, _ ->
                        // ドラッグ認識後は消費して、下位のスクロール等と競合させない。
                        change.consume()
                        dragState.drag(change.position)
                    },
                    onDragEnd = {
                        runCatching {
                            dragState.end()?.let { (src, dst) -> onMerge(src, dst) }
                        }.onFailure { logger.warn(it) { "drag end failed" } }
                    },
                    onDragCancel = { dragState.cancel() },
                )
            }
            .padding(2.dp),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(author, fontWeight = FontWeight.Bold)
            VerticalDivider(modifier = Modifier.height(16.dp).padding(horizontal = 4.dp))
            Text(title, fontWeight = FontWeight.Bold)
        }
        if (thumbnails != null) {
            ThumbnailStrip(thumbnails)
        }
    }
}

/**
 * 表紙を等倍表示し、その右に2枚目以降を重ね描きした帯を並べるサムネイル列。
 * 帯は合成済みのビットマップ1枚なので、実ピクセルサイズをそのまま表示サイズに使う。
 */
@Composable
private fun ThumbnailStrip(thumbnails: CardThumbnails) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val cover = thumbnails.cover
        val (coverWidth, coverHeight) = fitSize(cover.width, cover.height, COVER_FIT_WIDTH, COVER_FIT_HEIGHT)
        Image(
            bitmap = cover,
            contentDescription = null,
            modifier = Modifier.size(coverWidth.dp, coverHeight.dp),
        )

        val pageStrip = thumbnails.pageStrip ?: return@Row
        VerticalDivider(modifier = Modifier.height(PAGE_FIT_SIZE.dp).padding(horizontal = 4.dp))
        with(LocalDensity.current) {
            Image(
                bitmap = pageStrip,
                contentDescription = null,
                modifier = Modifier.size(pageStrip.width.toDp(), pageStrip.height.toDp()),
            )
        }
    }
}

/**
 * サムネイル画像を表示用の [CardThumbnails] へ変換する。画像が1枚も無ければ null。
 */
private fun buildCardThumbnails(thumbnails: List<BufferedImage>, density: Density): CardThumbnails? {
    val cover = thumbnails.firstOrNull() ?: return null
    return CardThumbnails(
        cover = cover.toComposeImageBitmap(),
        pageStrip = buildPageStrip(thumbnails.drop(1), density),
    )
}

/**
 * ページのサムネイルを右へ [PAGE_OVERLAP_STEP] ずつずらして重ね描きした帯を1枚へ合成する。
 * ページ数ぶんの drawImage を毎フレーム実行させないため、ここで一度だけ描いて以降は使い回す。
 * ページが無ければ null。
 */
private fun buildPageStrip(pages: List<BufferedImage>, density: Density): ImageBitmap? {
    if (pages.isEmpty()) {
        return null
    }
    val logicalWidth = PAGE_FIT_SIZE + (pages.size - 1) * PAGE_OVERLAP_STEP
    val scale = density.density
    val widthPx = (logicalWidth * scale).roundToInt()
    val heightPx = (PAGE_FIT_SIZE * scale).roundToInt()
    val strip = ImageBitmap(widthPx, heightPx)
    CanvasDrawScope().draw(
        density = density,
        layoutDirection = LayoutDirection.Ltr,
        canvas = Canvas(strip),
        size = Size(widthPx.toFloat(), heightPx.toFloat()),
    ) {
        // ImageBitmap への変換もループ内で行ない、全ページ分を同時に抱えないようにする。
        pages.asReversed().forEachIndexed { index, page ->
            val (pageWidth, pageHeight) = fitSize(page.width, page.height, PAGE_FIT_SIZE, PAGE_FIT_SIZE)
            val logicalX = logicalWidth - PAGE_FIT_SIZE - PAGE_OVERLAP_STEP * index
            drawImage(
                image = page.toComposeImageBitmap(),
                dstOffset = IntOffset((logicalX * scale).roundToInt(), 0),
                dstSize = IntSize((pageWidth * scale).roundToInt(), (pageHeight * scale).roundToInt()),
            )
            val edgeX = (logicalX + pageWidth) * scale
            drawLine(
                color = UnselectedBorder,
                start = Offset(edgeX, 0f),
                end = Offset(edgeX, heightPx.toFloat()),
                strokeWidth = 1f,
            )
        }
    }
    return strip
}

/**
 * 長い文字列を先頭と末尾を残して中央を省略する（一覧の折り返しを抑える）。
 */
internal fun truncateForDisplay(text: String, maxLength: Int): String {
    if (text.length <= maxLength) {
        return text
    }
    val ellipsis = " … "
    val remainingLength = maxLength - ellipsis.length
    val frontLength = remainingLength / 2
    val backLength = remainingLength - frontLength
    return text.take(frontLength) + ellipsis + text.takeLast(backLength)
}

/**
 * fitX × fitY に収まる最大サイズ（アスペクト比維持）を返す。
 */
internal fun fitSize(width: Int, height: Int, fitX: Float, fitY: Float): Pair<Float, Float> {
    val ratio = minOf(fitX / width, fitY / height)
    return width * ratio to height * ratio
}
