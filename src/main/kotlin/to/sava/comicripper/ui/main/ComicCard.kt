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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.onClick
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
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
 *
 * [files] と [revision] はこの画像を作った元の状態で、作り直しが必要かの判定と
 * 継ぎ足しの可否判定に使う。
 */
@Immutable
private class CardThumbnails(
    val files: List<String>,
    val revision: Int,
    val cover: ImageBitmap,
    val pageStrip: ImageBitmap?,
)

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
 *   選択中のときだけ呼ぶ。選択が移ると移動元と移動先が再配置されるため、移動先が必ず通知する。
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
    val author = remember(comic.author) { truncateForDisplay(comic.author, MAX_AUTHOR_LENGTH) }
    val title = remember(comic.title) { truncateForDisplay(comic.title, MAX_TITLE_LENGTH) }

    val thumbnails = rememberCardThumbnails(comic)

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
                if (selected) {
                    onBoundsInParent(it.boundsInParent())
                }
                dragState.register(comic.id, it.positionInWindow(), it.boundsInWindow())
            }
            .background(if (selected) SelectedBackground else UnselectedBackground)
            .border(1.dp, borderColor)
            // 選択は押した時点で確定させる。onClick へ載せるとダブルクリックの判定時間ぶん
            // 待ってからの発火になり、キーやホイールでの移動に比べて明らかに遅れる。
            .pointerInput(comic.id) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onSelect()
                }
            }
            .onClick(onDoubleClick = onOpen) {}
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
 * カードの表示画像を用意する。
 *
 * デコードと合成はページ数に比例した時間がかかるためコンポジションの外で行ない、
 * 出来上がったものへ差し替える。作り直しの間は前の画像を出したままにして、ちらつかせない。
 */
@Composable
private fun rememberCardThumbnails(comic: Comic): CardThumbnails? {
    val density = LocalDensity.current
    val files = comic.files
    val revision = comic.imageRevision
    return produceState<CardThumbnails?>(null, comic, files, density, revision) {
        value = try {
            withContext(Dispatchers.Default) { buildCardThumbnails(comic, value, files, density, revision) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // 画像変換系の例外はホスト全体を道連れにするため、ここで止めて前の画像を残す。
            logger.warn(e) { "thumbnail build failed: ${comic.id}" }
            value
        }
    }.value
}

/**
 * 構成ファイルを読んで表示用の画像を作る。ファイルが1枚も無ければ null。
 *
 * [previous] が使える場合（表紙が同じで、ページが末尾に増えただけ）は帯を継ぎ足して済ませる。
 * 重ね描きの x 座標は末尾からの位置で決まるので、ページを末尾に足しても既存ページの位置は動かない。
 */
private suspend fun buildCardThumbnails(
    comic: Comic,
    previous: CardThumbnails?,
    files: List<String>,
    density: Density,
    revision: Int,
): CardThumbnails? {
    if (files.isEmpty()) {
        return null
    }
    val reusable = previous?.takeIf { it.revision == revision && it.files.isAppendPrefixOf(files) }
    val addedFrom = reusable?.files?.size ?: 0
    val pages = coroutineScope {
        files.drop(maxOf(1, addedFrom))
            .map { filename -> async { comic.loadThumbnail(filename) } }
            .awaitAll()
            .filterNotNull()
    }
    val cover = reusable?.cover
        ?: comic.loadThumbnail(files.first())?.toComposeImageBitmap()
        ?: return null
    return CardThumbnails(
        files = files,
        revision = revision,
        cover = cover,
        pageStrip = buildPageStrip(reusable?.pageStrip, pages, density),
    )
}

/** 先頭が完全に一致していて、末尾に足されただけかどうか。 */
private fun List<String>.isAppendPrefixOf(other: List<String>): Boolean =
    isNotEmpty() && other.size > size && other.subList(0, size) == this

/**
 * ページを右へ [PAGE_OVERLAP_STEP] ずつずらして重ね描きした帯を1枚へ合成する。
 * ページ数ぶんの drawImage を毎フレーム実行させないため、ここで一度だけ描いて以降は使い回す。
 *
 * [existing] を渡すと、その右側へ [pages] を継ぎ足した帯を作る。継ぎ足すページは既存ページより
 * 奥（先に描く側）になるため、新しいページを描いた上に既存の帯を重ねる。
 */
private fun buildPageStrip(existing: ImageBitmap?, pages: List<BufferedImage>, density: Density): ImageBitmap? {
    if (existing == null && pages.isEmpty()) {
        return null
    }
    val scale = density.density
    val heightPx = (PAGE_FIT_SIZE * scale).roundToInt()
    val existingWidthPx = existing?.width ?: 0
    val addedWidth = if (existing == null) {
        PAGE_FIT_SIZE + (pages.size - 1) * PAGE_OVERLAP_STEP
    } else {
        pages.size * PAGE_OVERLAP_STEP
    }
    val widthPx = existingWidthPx + (addedWidth * scale).roundToInt()
    val strip = ImageBitmap(widthPx, heightPx)
    CanvasDrawScope().draw(
        density = density,
        layoutDirection = LayoutDirection.Ltr,
        canvas = Canvas(strip),
        size = Size(widthPx.toFloat(), heightPx.toFloat()),
    ) {
        // 帯の右端が最後のページ。末尾から数えた位置がそのまま x を決める。
        val logicalWidth = widthPx / scale
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
        if (existing != null) {
            drawImage(
                image = existing,
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(existing.width, existing.height),
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
