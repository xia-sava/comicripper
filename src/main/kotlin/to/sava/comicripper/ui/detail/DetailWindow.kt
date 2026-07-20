package to.sava.comicripper.ui.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.onClick
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import to.sava.comicripper.VERSION
import to.sava.comicripper.domain.model.Comic
import to.sava.comicripper.infrastructure.repository.ComicRepository
import to.sava.comicripper.model.Setting
import to.sava.comicripper.ui.BringToFrontOnFirstShow
import to.sava.comicripper.ui.ComicRipperTheme
import to.sava.comicripper.ui.ComicRipperWindow
import to.sava.comicripper.ui.CompactButton
import to.sava.comicripper.ui.CompactOutlinedTextField
import to.sava.comicripper.ui.CompactSlider
import to.sava.comicripper.ui.ComposeWindowHost
import to.sava.comicripper.ui.ErrorToast
import to.sava.comicripper.ui.ProgressOverlay
import to.sava.comicripper.ui.cutter.showCutterWindow
import to.sava.comicripper.ui.rememberErrorToastState
import to.sava.comicripper.ui.rememberProgressOverlayState
import to.sava.comicripper.ui.rememberWindowIconPainter
import java.io.File
import kotlin.math.roundToInt

private val logger = KotlinLogging.logger {}

private const val WINDOW_TITLE = "comicripper $VERSION"

/**
 * Detail ウィンドウを開く。
 * Comic ごとに1枚まで（同一 Comic で既に開いていれば何もしない）。
 * 任意のスレッドから呼び出せる。
 * owner を渡すとそのウィンドウのオーナー付きダイアログとして開き、owner が背面に固定される。
 */
fun showDetailWindow(comic: Comic, owner: java.awt.Window? = null) {
    ComposeWindowHost.show(key = "detail:${comic.id}") { onCloseRequest ->
        DetailWindow(comic = comic, owner = owner, onCloseRequest = onCloseRequest)
    }
}

/**
 * 詳細画面。
 * ページ画像のビューアと、作者名・題名・ISBN の編集、
 * 画像の削除/リリース/リロード、ISBN検索・OCR・表紙カット・ZIP作成を行なう。
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun DetailWindow(comic: Comic, owner: java.awt.Window?, onCloseRequest: () -> Unit) {
    val setting: Setting = koinInject()

    val state = rememberWindowState(
        size = DpSize(setting.detailWindowWidth.dp, setting.detailWindowHeight.dp),
        position = if (setting.detailWindowPosX >= 0.0) {
            WindowPosition.Absolute(setting.detailWindowPosX.dp, setting.detailWindowPosY.dp)
        } else {
            WindowPosition.PlatformDefault
        },
    )
    LaunchedEffect(state) {
        snapshotFlow { state.size }.collect { size ->
            setting.detailWindowWidth = size.width.value.toDouble()
            setting.detailWindowHeight = size.height.value.toDouble()
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.position }.collect { position ->
            if (position is WindowPosition.Absolute) {
                setting.detailWindowPosX = position.x.value.toDouble()
                setting.detailWindowPosY = position.y.value.toDouble()
            }
        }
    }

    val repos: ComicRepository = koinInject()
    val errorToast = rememberErrorToastState()
    val progress = rememberProgressOverlayState(onError = { title -> errorToast.show("${title}に失敗しました") })
    val uiScope = rememberCoroutineScope()

    var authorText by remember { mutableStateOf(comic.author) }
    var titleText by remember { mutableStateOf(comic.title) }
    var isbnText by remember { mutableStateOf("") }

    // comic.files は呼び出しごとに新しいスナップショットを返すため、
    // sizeチェックとインデックスアクセスは必ず同じスナップショットに対して行なうこと
    // （別々に読むと並行削除でIndexOutOfBoundsExceptionを起こしうる）。
    val initialFiles = remember { comic.files }
    var files by remember { mutableStateOf(initialFiles) }
    var currentPage by remember {
        mutableStateOf(if (initialFiles.size > 1 && initialFiles[1] == comic.coverFull) 1 else 0)
    }

    LaunchedEffect(comic) {
        comic.changeFlow.collect {
            if (authorText != comic.author) {
                authorText = comic.author
            }
            if (titleText != comic.title) {
                titleText = comic.title
            }
            val newFiles = comic.files
            files = newFiles
            if (newFiles.isEmpty()) {
                onCloseRequest()
            } else if (currentPage >= newFiles.size) {
                currentPage = newFiles.size - 1
            }
        }
    }

    val pageCount = files.size
    val currentFilename = files.getOrNull(currentPage)

    // 直前に表示した1枚の (ファイル名, ImageBitmap) を保持する。
    // フルサイズ BufferedImage 自体は Comic.imageCache が保持するので、ここは変換結果のみ。
    var loadedImage by remember { mutableStateOf<Pair<String, ImageBitmap>?>(null) }
    LaunchedEffect(currentFilename) {
        val filename = currentFilename ?: return@LaunchedEffect
        if (loadedImage?.first == filename) {
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            runCatching { comic.getFullSizeImage(filename).toComposeImageBitmap() }
                .onFailure { logger.warn(it) { "detail image load failed: $filename" } }
                .getOrNull()
        }?.let { loadedImage = filename to it }
    }

    fun updateAuthor(value: String) {
        authorText = value
        comic.author = value
    }

    fun updateTitle(value: String) {
        titleText = value
        comic.title = value
    }

    fun firstImage() {
        currentPage = 0
    }

    fun leftImage() {
        if (currentPage > 0) {
            currentPage -= 1
        }
    }

    fun rightImage() {
        if (currentPage < pageCount - 1) {
            currentPage += 1
        }
    }

    fun lastImage() {
        if (pageCount > 0) {
            currentPage = pageCount - 1
        }
    }

    fun deleteCurrentImage() {
        // onPreviewKeyEvent 経由の呼び出しは生成時点のクロージャで実行されうるため、
        // currentFilename を直接キャプチャせず、呼び出し時点の files/currentPage から都度求める。
        files.getOrNull(currentPage)?.let { filename ->
            File("${setting.workDirectory}/$filename").delete()
        }
    }

    fun releaseCurrentImage() {
        currentFilename?.let { filename ->
            repos.releaseFile(comic, filename)
        }
    }

    fun reloadImages() {
        uiScope.launch(Dispatchers.IO) {
            try {
                comic.reloadImages()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logger.warn(e) { "reloadImages failed" }
                errorToast.show("画像リロードに失敗しました")
            }
        }
    }

    fun searchIsbn() {
        val isbn = isbnText
        if (isbn.isEmpty()) {
            return
        }
        progress.launchTask("ISBN検索", "ISBN から著者名/作品名をサーチしてます") {
            val (searchedAuthor, searchedTitle) = repos.searchISBN(isbn)
            comic.author = searchedAuthor
            comic.title = searchedTitle
        }
    }

    fun ocrIsbn() {
        progress.launchTask("OCRしています", "画像から ISBN を読み取って著者名/作品名をサーチしてます") {
            repos.ocrISBN(comic)?.let { (ocrAuthor, ocrTitle) ->
                comic.author = ocrAuthor
                comic.title = ocrTitle
            }
        }
    }

    fun createZip() {
        progress.launchTask("ZIPしています", "コミックをZIP化しています") {
            repos.zipComic(comic)
            onCloseRequest()
        }
    }

    val sliderFocus = remember { FocusRequester() }
    val isbnFocus = remember { FocusRequester() }

    ComicRipperWindow(
        onCloseRequest = onCloseRequest,
        state = state,
        title = "$titleText $authorText - $WINDOW_TITLE",
        icon = rememberWindowIconPainter(),
        owner = owner,
        onPreviewKeyEvent = { event ->
            when {
                progress.isActive -> true
                event.type != KeyEventType.KeyDown -> false
                event.key == Key.Escape -> {
                    onCloseRequest()
                    true
                }
                event.isCtrlPressed && event.key == Key.D -> {
                    deleteCurrentImage()
                    true
                }
                else -> false
            }
        },
    ) {
        BringToFrontOnFirstShow()
        LaunchedEffect(Unit) {
            if (comic.author.startsWith("coverF_") || comic.author == "ISBN不明") {
                isbnFocus.requestFocus()
            } else {
                sliderFocus.requestFocus()
            }
        }
        ComicRipperTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .onPointerEvent(PointerEventType.Scroll) { event ->
                                val deltaY = event.changes.first().scrollDelta.y
                                when {
                                    deltaY < 0f -> leftImage()
                                    deltaY > 0f -> rightImage()
                                }
                            },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("作者:")
                            ToolbarTextField(authorText, { updateAuthor(it) }, 150.dp, onEnter = onCloseRequest)
                            Text("題名:")
                            ToolbarTextField(titleText, { updateTitle(it) }, 300.dp, onEnter = onCloseRequest)
                            Spacer(modifier = Modifier.weight(1.0f))
                            CompactButton(onClick = { deleteCurrentImage() }) { Text("画像削除") }
                            CompactButton(onClick = { releaseCurrentImage() }) { Text("画像リリース") }
                            CompactButton(onClick = { reloadImages() }) { Text("画像リロード") }
                            VerticalDivider(modifier = Modifier.height(24.dp))
                            ToolbarTextField(
                                isbnText,
                                { isbnText = it },
                                100.dp,
                                onEnter = { searchIsbn() },
                                modifier = Modifier.focusRequester(isbnFocus),
                            )
                            CompactButton(onClick = { searchIsbn() }) { Text("ISBN検索") }
                            VerticalDivider(modifier = Modifier.height(24.dp))
                            CompactButton(onClick = { ocrIsbn() }) { Text("OCR") }
                            VerticalDivider(modifier = Modifier.height(24.dp))
                            CompactButton(onClick = { showCutterWindow(comic, owner = window) }) { Text("表紙カット") }
                            VerticalDivider(modifier = Modifier.height(24.dp))
                            CompactButton(onClick = { createZip() }) { Text("ZIP作成") }
                            VerticalDivider(modifier = Modifier.height(24.dp))
                            CompactButton(onClick = onCloseRequest) { Text("閉じる") }
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1.0f)
                                .onClick(
                                    onDoubleClick = {
                                        if (comic.coverFull.isNullOrEmpty().not() &&
                                            comic.coverAlbum.isNullOrEmpty()
                                        ) {
                                            showCutterWindow(comic, owner = window)
                                        }
                                    },
                                    onClick = { sliderFocus.requestFocus() },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            loadedImage?.let { (_, bitmap) ->
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CompactButton(onClick = { firstImage() }) { Text("◀◀") }
                            CompactButton(onClick = { leftImage() }) { Text("◀") }
                            Box(
                                modifier = Modifier.weight(1.0f),
                                contentAlignment = Alignment.Center,
                            ) {
                                CompactSlider(
                                    value = currentPage.toFloat(),
                                    onValueChange = { value ->
                                        currentPage = value.roundToInt()
                                            .coerceIn(0, (pageCount - 1).coerceAtLeast(0))
                                    },
                                    valueRange = 0f..(pageCount - 1).coerceAtLeast(0).toFloat(),
                                    steps = 0,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .focusRequester(sliderFocus)
                                        .onPreviewKeyEvent { event ->
                                            when {
                                                event.type != KeyEventType.KeyDown -> false
                                                event.key == Key.DirectionLeft -> {
                                                    leftImage()
                                                    true
                                                }
                                                event.key == Key.DirectionRight -> {
                                                    rightImage()
                                                    true
                                                }
                                                else -> false
                                            }
                                        },
                                )
                                Text("${currentPage + 1} / $pageCount (${currentFilename ?: ""})")
                            }
                            CompactButton(onClick = { rightImage() }) { Text("▶") }
                            CompactButton(onClick = { lastImage() }) { Text("▶▶") }
                        }
                    }
                    ProgressOverlay(progress)
                    ErrorToast(errorToast)
                }
            }
        }
    }
}

/**
 * ツールバー用の1行テキストフィールド。
 * Enter / NumPadEnter を onEnter に割り当てる。
 */
@Composable
private fun ToolbarTextField(
    value: String,
    onValueChange: (String) -> Unit,
    width: Dp,
    onEnter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    CompactOutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        modifier = modifier
            .width(width)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown &&
                    (event.key == Key.Enter || event.key == Key.NumPadEnter)
                ) {
                    onEnter()
                    true
                } else {
                    false
                }
            },
    )
}
