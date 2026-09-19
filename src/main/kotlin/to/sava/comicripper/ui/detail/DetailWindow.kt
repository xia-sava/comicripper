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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.ImageBitmap
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
import androidx.compose.ui.unit.dp
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import to.sava.comicripper.VERSION
import to.sava.comicripper.domain.model.Comic
import to.sava.comicripper.infrastructure.image.ComicImageStore
import to.sava.comicripper.infrastructure.repository.ComicRepository
import to.sava.comicripper.infrastructure.repository.ComicStorage
import to.sava.comicripper.infrastructure.repository.ImageTrash
import to.sava.comicripper.infrastructure.repository.UndoResult
import to.sava.comicripper.infrastructure.service.BookInfoSearcher
import to.sava.comicripper.model.Setting
import to.sava.comicripper.ui.BringToFrontOnShow
import to.sava.comicripper.ui.ComicRipperTheme
import to.sava.comicripper.ui.ComicRipperWindow
import to.sava.comicripper.ui.CompactButton
import to.sava.comicripper.ui.CompactOutlinedTextField
import to.sava.comicripper.ui.CompactSlider
import to.sava.comicripper.ui.CompactTooltipArea
import to.sava.comicripper.ui.ComposeWindowHost
import to.sava.comicripper.ui.ErrorToast
import to.sava.comicripper.ui.KeyRepeatDetector
import to.sava.comicripper.ui.ResetOnFocusLost
import to.sava.comicripper.ui.ProgressOverlay
import to.sava.comicripper.ui.cutter.showCutterWindow
import to.sava.comicripper.ui.main.selectionAfterMove
import to.sava.comicripper.ui.rememberErrorToastState
import to.sava.comicripper.ui.rememberPersistedWindowState
import to.sava.comicripper.ui.rememberProgressOverlayState
import to.sava.comicripper.ui.rememberWindowIconPainter
import to.sava.comicripper.ui.toDisplayImageBitmap
import kotlin.math.roundToInt

private val logger = KotlinLogging.logger {}

private const val WINDOW_TITLE = "comicripper $VERSION"

/**
 * 表示中の1枚。[key] は「読み直しの世代 + ファイル名」で、画像リロード後に同じファイル名でも
 * 読み直すために世代を含める。
 */
private class DisplayedImage(val key: String, val bitmap: ImageBitmap) {
    companion object {
        fun keyOf(revision: Int, filename: String) = "$revision/$filename"
    }
}

/** ホイールを止めていたとみなす間隔。これ以上の間を空けて回したときだけ、端を越えて前後の本へ移る。 */
private const val WHEEL_REST_MILLIS = 500L

private fun detailWindowKey(comic: Comic) = "detail:${comic.id}"

/**
 * Detail ウィンドウを開く。
 * Comic ごとに1枚まで（同一 Comic で既に開いていればそれを前面へ出す）。
 * 任意のスレッドから呼び出せる。
 * owner を渡すとそのウィンドウのオーナー付きダイアログとして開き、owner が背面に固定される。
 */
fun showDetailWindow(comic: Comic, owner: java.awt.Window? = null) {
    ComposeWindowHost.show(key = detailWindowKey(comic)) { onCloseRequest ->
        DetailWindow(initialComic = comic, owner = owner, onCloseRequest = onCloseRequest)
    }
}

/**
 * 詳細画面。
 * ページ画像のビューアと、作者名・題名・ISBN の編集、
 * 画像の削除/リリース/リロード、ISBN検索・OCR・表紙カット・ZIP作成を行なう。
 * 一覧の前後の本へは、ウィンドウを開き直さずに表示を切り替えて移る。
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun DetailWindow(initialComic: Comic, owner: java.awt.Window?, onCloseRequest: () -> Unit) {
    val setting: Setting = koinInject()

    val state = rememberPersistedWindowState(setting.detailWindow)

    val repos: ComicRepository = koinInject()
    val comicStorage: ComicStorage = koinInject()
    val imageTrash: ImageTrash = koinInject()
    val imageStore: ComicImageStore = koinInject()
    val bookInfoSearcher: BookInfoSearcher = koinInject()
    val errorToast = rememberErrorToastState()
    val progress = rememberProgressOverlayState(onError = { title -> errorToast.show("${title}に失敗しました") })

    // 表示中の本。本ごとの表示状態は、これをキーに remember して本が替わったら作り直す。
    var comic by remember { mutableStateOf(initialComic) }
    // 本を切り替えたときに表示するページ。無ければ表紙を表示する。
    var pageOnSwitch by remember { mutableStateOf<String?>(null) }

    var isbnText by remember(comic) { mutableStateOf("") }

    // 同じリストに対して size チェックとインデックスアクセスを行なうこと
    // （別々に読むと並行削除で IndexOutOfBoundsException を起こしうる）。
    val files = comic.files
    var currentPage by remember(comic) {
        mutableStateOf(
            pageOnSwitch?.let { files.indexOf(it) }?.takeIf { it >= 0 }
                ?: if (files.size > 1 && files[1] == comic.coverFull) 1 else 0
        )
    }

    // 表示中のページが削除されて範囲外になったら末尾へ寄せ、全部消えたら画面を閉じる。
    LaunchedEffect(files) {
        if (files.isEmpty()) {
            onCloseRequest()
        } else if (currentPage >= files.size) {
            currentPage = files.size - 1
        }
    }

    val pageCount = files.size
    val currentFilename = files.getOrNull(currentPage)

    // 直前に表示した1枚を保持する。
    // フルサイズ BufferedImage 自体は Comic.imageCache が保持するので、ここは変換結果のみ。
    var loadedImage by remember(comic) { mutableStateOf<DisplayedImage?>(null) }
    LaunchedEffect(currentFilename, comic.imageRevision) {
        val filename = currentFilename ?: return@LaunchedEffect
        val key = DisplayedImage.keyOf(comic.imageRevision, filename)
        if (loadedImage?.key == key) {
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            try {
                imageStore.getFullSizeImage(filename).toDisplayImageBitmap()
            } catch (e: Exception) {
                logger.warn(e) { "detail image load failed: $filename" }
                null
            }
        }?.let { loadedImage = DisplayedImage(key, it) }
    }

    fun updateAuthor(value: String) {
        comic.author = value
    }

    fun updateTitle(value: String) {
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
            if (!imageTrash.delete(comic, filename)) {
                errorToast.show("画像を削除できませんでした")
            }
        }
    }

    fun releaseCurrentImage() {
        // deleteCurrentImage と同じく、呼び出し時点の files/currentPage から求める。
        files.getOrNull(currentPage)?.let { filename ->
            repos.releaseFile(comic, filename)
        }
    }

    fun reloadImages() {
        repos.reloadImages(comic)
    }

    // 時間のかかる処理は、始めた時点の本を対象に最後まで行なう。
    fun searchIsbn() {
        val target = comic
        val isbn = isbnText
        if (isbn.isEmpty()) {
            return
        }
        progress.launchTask("ISBN検索", "ISBN から著者名/作品名をサーチしてます") {
            val (searchedAuthor, searchedTitle) = bookInfoSearcher.search(isbn)
            target.author = searchedAuthor
            target.title = searchedTitle
        }
    }

    fun ocrIsbn() {
        val target = comic
        if (target.coverFull.isNullOrEmpty()) {
            errorToast.show("OCR対象の表紙画像がありません")
            return
        }
        progress.launchTask("OCRしています", "画像から ISBN を読み取って著者名/作品名をサーチしてます") {
            repos.ocrISBN(target)?.let { (ocrAuthor, ocrTitle) ->
                target.author = ocrAuthor
                target.title = ocrTitle
            }
        }
    }

    fun createZip() {
        val target = comic
        progress.launchTask("ZIPしています", "コミックをZIP化しています") {
            repos.zipComic(target)
            onCloseRequest()
        }
    }

    /**
     * [target] の [page] を表示し、一覧の選択もその本へ移す。[page] が無ければ表紙を表示する。
     * [target] が別の詳細画面で開いていれば、そちらを前面へ出してこの画面を閉じる。
     */
    fun showComic(target: Comic, page: String? = null) {
        val current = comic
        if (target.id == current.id) {
            page?.let { current.files.indexOf(it) }?.takeIf { it >= 0 }?.let { currentPage = it }
        } else if (ComposeWindowHost.rekey(from = detailWindowKey(current), to = detailWindowKey(target))) {
            pageOnSwitch = page
            comic = target
        } else {
            showDetailWindow(target)
            onCloseRequest()
        }
        comicStorage.targetId = target.id
    }

    /** 一覧で [direction] 側の隣の本へ移る。 */
    fun moveComic(direction: Int) {
        val current = comic
        comicStorage[selectionAfterMove(comicStorage.all.map { it.id }, current.id, direction)]
            ?.takeIf { it.id != current.id }
            ?.let { showComic(it) }
    }

    /** 最後に削除した画像を戻して表示する。 */
    fun undoDelete() {
        when (val result = imageTrash.undo()) {
            is UndoResult.Restored -> showComic(result.comic, result.filename)
            is UndoResult.Failed -> errorToast.show("${result.filename} を戻せませんでした")
            UndoResult.NothingToUndo -> Unit
        }
    }

    fun movePage(direction: Int, canCrossComic: Boolean) {
        when (val move = pageMove(currentPage, files.size, direction, canCrossComic)) {
            is PageMove.ToPage -> currentPage = move.page
            PageMove.ToPreviousComic -> moveComic(-1)
            PageMove.ToNextComic -> moveComic(1)
            PageMove.Stay -> Unit
        }
    }

    var lastWheelMillis by remember { mutableStateOf<Long?>(null) }

    // 入力欄の外ではページ表示のスライダーにフォーカスを置く。
    val sliderFocus = remember { FocusRequester() }
    val authorFocus = remember { FocusRequester() }
    val isbnFocus = remember { FocusRequester() }
    var focusedTextField by remember { mutableStateOf<DetailTextField?>(null) }

    fun Modifier.trackingFocus(field: DetailTextField) = onFocusChanged { focusState ->
        if (focusState.hasFocus) {
            focusedTextField = field
        } else if (focusedTextField == field) {
            focusedTextField = null
        }
    }

    fun leaveTextField() {
        sliderFocus.requestFocus()
    }

    // キー入力から表紙カット画面を開く際の owner（自ウィンドウ）。content 側で確定させる。
    var ownerWindow by remember { mutableStateOf<java.awt.Window?>(null) }

    fun runKeyAction(action: DetailKeyAction, isRepeat: Boolean) {
        when (action) {
            // 押したままでは端で止め、押し直したときに前後の本へ移る。
            DetailKeyAction.PreviousPage -> movePage(-1, canCrossComic = !isRepeat)
            DetailKeyAction.NextPage -> movePage(1, canCrossComic = !isRepeat)
            DetailKeyAction.PreviousComic -> moveComic(-1)
            DetailKeyAction.NextComic -> moveComic(1)
            DetailKeyAction.FirstPage -> firstImage()
            DetailKeyAction.LastPage -> lastImage()
            DetailKeyAction.DeleteImage -> deleteCurrentImage()
            DetailKeyAction.UndoDelete -> undoDelete()
            DetailKeyAction.ReleaseImage -> releaseCurrentImage()
            DetailKeyAction.ReloadImages -> reloadImages()
            DetailKeyAction.Ocr -> ocrIsbn()
            DetailKeyAction.CutCover -> showCutterWindow(comic, owner = ownerWindow)
            DetailKeyAction.FocusAuthor -> authorFocus.requestFocus()
            DetailKeyAction.FocusIsbn -> isbnFocus.requestFocus()
            DetailKeyAction.LeaveTextField -> leaveTextField()
            DetailKeyAction.Close -> onCloseRequest()
        }
    }

    val keyRepeat = remember { KeyRepeatDetector() }

    ComicRipperWindow(
        onCloseRequest = onCloseRequest,
        state = state,
        title = "${comic.title} ${comic.author} - $WINDOW_TITLE",
        icon = rememberWindowIconPainter(),
        owner = owner,
        onPreviewKeyEvent = { event ->
            // 進捗中に離したキーも取りこぼさないよう、遮断より先に押下状態を追う。
            val isRepeat = keyRepeat.onKeyEvent(event.type, event.key)
            when {
                progress.isActive -> true
                event.type != KeyEventType.KeyDown -> false
                else -> detailKeyAction(event.key, event.isCtrlPressed, isEditingText = focusedTextField != null)
                    ?.let { action ->
                        if (action.repeatable || !isRepeat) {
                            runKeyAction(action, isRepeat)
                        }
                        true
                    }
                    ?: false
            }
        },
    ) {
        BringToFrontOnShow()
        LaunchedEffect(window) { ownerWindow = window }
        ResetOnFocusLost(keyRepeat)
        LaunchedEffect(comic) {
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
                                val change = event.changes.firstOrNull() ?: return@onPointerEvent
                                // 回し続けている間は端で止め、いったん止めてから回したときに前後の本へ移る。
                                val canCrossComic = lastWheelMillis
                                    ?.let { change.uptimeMillis - it >= WHEEL_REST_MILLIS }
                                    ?: true
                                lastWheelMillis = change.uptimeMillis
                                when {
                                    change.scrollDelta.y < 0f -> movePage(-1, canCrossComic)
                                    change.scrollDelta.y > 0f -> movePage(1, canCrossComic)
                                }
                            },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // 上段にはこの本の書誌と、この本全体への操作を置く。
                            CompactTooltipArea("F2") { Text("作者:") }
                            ToolbarTextField(
                                comic.author,
                                { updateAuthor(it) },
                                150.dp,
                                onEnter = { leaveTextField() },
                                modifier = Modifier
                                    .focusRequester(authorFocus)
                                    .trackingFocus(DetailTextField.Author),
                            )
                            Text("題名:")
                            ToolbarTextField(
                                comic.title,
                                { updateTitle(it) },
                                300.dp,
                                onEnter = { leaveTextField() },
                                modifier = Modifier.trackingFocus(DetailTextField.Title),
                            )
                            VerticalDivider(modifier = Modifier.height(24.dp))
                            ToolbarTextField(
                                isbnText,
                                { isbnText = it },
                                100.dp,
                                onEnter = {
                                    searchIsbn()
                                    leaveTextField()
                                },
                                modifier = Modifier
                                    .focusRequester(isbnFocus)
                                    .trackingFocus(DetailTextField.Isbn),
                            )
                            CompactButton(onClick = { searchIsbn() }, tooltip = "ISBN欄で Enter（欄へは Ctrl+I）") {
                                Text("ISBN検索")
                            }
                            CompactButton(onClick = { ocrIsbn() }, tooltip = "Ctrl+O") { Text("OCR") }
                            Spacer(modifier = Modifier.weight(1.0f))
                            CompactButton(onClick = { showCutterWindow(comic, owner = window) }, tooltip = "Ctrl+T") {
                                Text("表紙カット")
                            }
                            CompactButton(onClick = { reloadImages() }, tooltip = "F5") { Text("画像リロード") }
                            VerticalDivider(modifier = Modifier.height(24.dp))
                            CompactButton(onClick = { createZip() }) { Text("ZIP作成") }
                            VerticalDivider(modifier = Modifier.height(24.dp))
                            CompactButton(onClick = onCloseRequest, tooltip = "Esc") { Text("閉じる") }
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
                            loadedImage?.let { displayed ->
                                Image(
                                    bitmap = displayed.bitmap,
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
                            // 下段にはページへの操作と、前後の本への移動を置く。
                            CompactButton(onClick = { firstImage() }, tooltip = "Home / Ctrl+A") { Text("◀◀") }
                            CompactButton(onClick = { leftImage() }, tooltip = "←") { Text("◀") }
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
                                        .focusRequester(sliderFocus),
                                )
                                Text("${currentPage + 1} / $pageCount (${currentFilename ?: ""})")
                            }
                            CompactButton(onClick = { rightImage() }, tooltip = "→") { Text("▶") }
                            CompactButton(onClick = { lastImage() }, tooltip = "End / Ctrl+E") { Text("▶▶") }
                            VerticalDivider(modifier = Modifier.height(24.dp))
                            CompactButton(onClick = { deleteCurrentImage() }, tooltip = "Del / Ctrl+D（Ctrl+Z で戻す）") {
                                Text("画像削除")
                            }
                            CompactButton(onClick = { releaseCurrentImage() }, tooltip = "Ctrl+L") {
                                Text("画像リリース")
                            }
                            VerticalDivider(modifier = Modifier.height(24.dp))
                            CompactButton(onClick = { moveComic(-1) }, tooltip = "PageUp") { Text("◀ 前の本") }
                            CompactButton(onClick = { moveComic(1) }, tooltip = "PageDown") { Text("次の本 ▶") }
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

/** 詳細画面の入力欄。 */
private enum class DetailTextField { Author, Title, Isbn }

/**
 * 詳細画面のキー操作で行なう処理。
 * [repeatable] でないものは、キーを押したままにしても最初の1回しか行なわない。
 */
internal enum class DetailKeyAction(val repeatable: Boolean = false) {
    PreviousPage(repeatable = true),
    NextPage(repeatable = true),
    PreviousComic,
    NextComic,
    FirstPage,
    LastPage,
    DeleteImage,
    UndoDelete,
    ReleaseImage,
    ReloadImages,
    Ocr,
    CutCover,
    FocusAuthor,
    FocusIsbn,
    LeaveTextField,
    Close,
}

/** 入力欄の編集中は、入力欄のカーソル移動と文字の削除に譲るキー。 */
private val TextEditingKeys = setOf(
    Key.DirectionLeft,
    Key.DirectionRight,
    Key.DirectionUp,
    Key.DirectionDown,
    Key.MoveHome,
    Key.MoveEnd,
    Key.Delete,
)

/** 入力欄の編集中は、入力欄の操作（全選択・取り消しなど）に譲る Ctrl 付きのキー。 */
private val TextEditingCtrlKeys = setOf(Key.A, Key.E, Key.Z)

/**
 * 詳細画面で押されたキーに対応する処理を返す。対応する処理が無ければ null を返す。
 * 入力欄の編集中は、入力欄の操作に使うキーには null を返して入力欄へ譲り、
 * Esc は画面を閉じずに入力欄から抜ける。
 */
internal fun detailKeyAction(key: Key, isCtrlPressed: Boolean, isEditingText: Boolean): DetailKeyAction? {
    if (isEditingText && (key in TextEditingKeys || (isCtrlPressed && key in TextEditingCtrlKeys))) {
        return null
    }
    return if (isCtrlPressed) {
        when (key) {
            Key.A -> DetailKeyAction.FirstPage
            Key.E -> DetailKeyAction.LastPage
            Key.D -> DetailKeyAction.DeleteImage
            Key.Z -> DetailKeyAction.UndoDelete
            Key.L -> DetailKeyAction.ReleaseImage
            Key.O -> DetailKeyAction.Ocr
            Key.T -> DetailKeyAction.CutCover
            Key.I -> DetailKeyAction.FocusIsbn
            else -> null
        }
    } else {
        when (key) {
            Key.DirectionLeft -> DetailKeyAction.PreviousPage
            Key.DirectionRight -> DetailKeyAction.NextPage
            Key.MoveHome -> DetailKeyAction.FirstPage
            Key.MoveEnd -> DetailKeyAction.LastPage
            Key.PageUp -> DetailKeyAction.PreviousComic
            Key.PageDown -> DetailKeyAction.NextComic
            Key.Delete -> DetailKeyAction.DeleteImage
            Key.F2 -> DetailKeyAction.FocusAuthor
            Key.F5 -> DetailKeyAction.ReloadImages
            Key.Escape -> if (isEditingText) DetailKeyAction.LeaveTextField else DetailKeyAction.Close
            else -> null
        }
    }
}

/** ページ送りの行き先。 */
internal sealed interface PageMove {
    data class ToPage(val page: Int) : PageMove
    data object ToPreviousComic : PageMove
    data object ToNextComic : PageMove
    data object Stay : PageMove
}

/**
 * ページを [direction] のぶんだけ送った行き先を返す。
 * 端を越える送りは前後の本へ移る。ただし [canCrossComic] が false のとき
 * （キーを押したまま・ホイールを回し続けている間）は、本を飛ばしていかないよう端に留まる。
 */
internal fun pageMove(currentPage: Int, pageCount: Int, direction: Int, canCrossComic: Boolean): PageMove {
    val destination = currentPage + direction
    return when {
        destination in 0 until pageCount -> PageMove.ToPage(destination)
        !canCrossComic -> PageMove.Stay
        destination < 0 -> PageMove.ToPreviousComic
        else -> PageMove.ToNextComic
    }
}
