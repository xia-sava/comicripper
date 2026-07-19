package to.sava.comicripper.ui.main

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.onClick
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import to.sava.comicripper.model.Comic
import kotlin.math.roundToInt

/** 選択時の背景（antiquewhite）と枠（黒）。common.css の .comic.selected に対応。 */
private val SelectedBackground = Color(0xFFFAEBD7)
private val SelectedBorder = Color.Black

/** 未選択時の背景（白）と枠（silver）。common.css の .comic に対応。 */
private val UnselectedBackground = Color.White
private val UnselectedBorder = Color(0xFFC0C0C0)

private const val MAX_AUTHOR_LENGTH = 20
private const val MAX_TITLE_LENGTH = 40

/** 表紙サムネイルの最大表示サイズ（1枚目）。 */
private const val COVER_FIT_WIDTH = 256f
private const val COVER_FIT_HEIGHT = 128f

/** 2枚目以降のサムネイルの最大表示サイズと重ね描きのずらし量。 */
private const val PAGE_FIT_SIZE = 128f
private const val PAGE_OVERLAP_STEP = 3f

/**
 * コミック1件を表すカード。
 * 著者名・題名と、表紙サムネイル＋2枚目以降の重ね描きを表示する。
 * クリックで選択、ダブルクリックで詳細/カット画面を開く。
 *
 * @param onBoundsInParent FlowRow 座標系での自身の矩形を親へ通知する
 *   （選択カードのスクロール補正に使う。スクロール位置に依存しない座標）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ComicCard(
    comic: Comic,
    selected: Boolean,
    onSelect: () -> Unit,
    onOpen: () -> Unit,
    onBoundsInParent: (Rect) -> Unit,
) {
    // changeFlow の発火ごとにインクリメントし、著者名/題名/サムネイルの再計算キーにする。
    var version by remember { mutableStateOf(0) }
    LaunchedEffect(comic) {
        comic.changeFlow.collect { version++ }
    }

    val author = remember(comic, version) { truncateForDisplay(comic.author, MAX_AUTHOR_LENGTH) }
    val title = remember(comic, version) { truncateForDisplay(comic.title, MAX_TITLE_LENGTH) }

    // BufferedImage → ImageBitmap 変換は重いので remember でキャッシュする
    // （非 Lazy リストで全カードが同時に compose されるため、毎回変換すると全カード分走る）。
    // 画像変換系の例外はホスト全体を道連れにするため runCatching で保護する。
    val thumbnails = remember(comic, version) {
        runCatching { comic.thumbnails.map { it.toComposeImageBitmap() } }
            .onFailure { println("thumbnail convert failed: ${it.message}") }
            .getOrDefault(emptyList())
    }

    Column(
        modifier = Modifier
            .onGloballyPositioned { onBoundsInParent(it.boundsInParent()) }
            .background(if (selected) SelectedBackground else UnselectedBackground)
            .border(1.dp, if (selected) SelectedBorder else UnselectedBorder)
            .onClick(onDoubleClick = onOpen, onClick = onSelect)
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
        if (thumbnails.isNotEmpty()) {
            ThumbnailStrip(thumbnails)
        }
    }
}

/**
 * 1枚目を等倍表示し、2枚目以降を右へ 3px ずつずらして重ね描きするサムネイル列。
 */
@Composable
private fun ThumbnailStrip(thumbnails: List<ImageBitmap>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        val cover = thumbnails.first()
        val (coverWidth, coverHeight) = fitSize(cover.width, cover.height, COVER_FIT_WIDTH, COVER_FIT_HEIGHT)
        Image(
            bitmap = cover,
            contentDescription = null,
            modifier = Modifier.size(coverWidth.dp, coverHeight.dp),
        )

        val pages = thumbnails.drop(1)
        if (pages.isEmpty()) {
            return@Row
        }
        VerticalDivider(modifier = Modifier.height(PAGE_FIT_SIZE.dp).padding(horizontal = 4.dp))

        val logicalWidth = PAGE_FIT_SIZE + (pages.size - 1) * PAGE_OVERLAP_STEP
        Canvas(modifier = Modifier.size(logicalWidth.dp, PAGE_FIT_SIZE.dp)) {
            // dp で確保した論理サイズと実ピクセルの比率。縦横同一（密度スケール）なので高さから求める。
            val scale = size.height / PAGE_FIT_SIZE
            pages.reversed().forEachIndexed { index, page ->
                val (pageWidth, pageHeight) = fitSize(page.width, page.height, PAGE_FIT_SIZE, PAGE_FIT_SIZE)
                val logicalX = logicalWidth - PAGE_FIT_SIZE - PAGE_OVERLAP_STEP * index
                drawImage(
                    image = page,
                    dstOffset = IntOffset((logicalX * scale).roundToInt(), 0),
                    dstSize = IntSize((pageWidth * scale).roundToInt(), (pageHeight * scale).roundToInt()),
                )
                val edgeX = (logicalX + pageWidth) * scale
                drawLine(
                    color = UnselectedBorder,
                    start = Offset(edgeX, 0f),
                    end = Offset(edgeX, size.height),
                    strokeWidth = 1f,
                )
            }
        }
    }
}

/**
 * 長い文字列を先頭と末尾を残して中央を省略する（一覧の折り返しを抑える）。
 */
private fun truncateForDisplay(text: String, maxLength: Int): String {
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
private fun fitSize(width: Int, height: Int, fitX: Float, fitY: Float): Pair<Float, Float> {
    val ratio = minOf(fitX / width, fitY / height)
    return width * ratio to height * ratio
}
