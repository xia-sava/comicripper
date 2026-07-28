package to.sava.comicripper.ui.main

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import org.koin.compose.koinInject
import to.sava.comicripper.VERSION
import to.sava.comicripper.application.ApplicationScope
import to.sava.comicripper.domain.model.Comic
import to.sava.comicripper.infrastructure.repository.ComicRepository
import to.sava.comicripper.infrastructure.repository.ComicStorage
import to.sava.comicripper.model.Setting
import to.sava.comicripper.ui.BringToFrontOnFirstShow
import to.sava.comicripper.ui.ComicRipperTheme
import to.sava.comicripper.ui.ComicRipperWindow
import to.sava.comicripper.ui.CompactButton
import to.sava.comicripper.ui.ComposeWindowHost
import to.sava.comicripper.ui.ErrorToast
import to.sava.comicripper.ui.ProgressOverlay
import to.sava.comicripper.ui.TextAreaOverlay
import to.sava.comicripper.ui.cutter.showCutterWindow
import to.sava.comicripper.ui.detail.showDetailWindow
import to.sava.comicripper.ui.rememberErrorToastState
import to.sava.comicripper.ui.rememberPersistedWindowState
import to.sava.comicripper.ui.rememberProgressOverlayState
import to.sava.comicripper.ui.rememberTextAreaOverlayState
import to.sava.comicripper.ui.rememberWindowIconPainter
import to.sava.comicripper.ui.setting.SettingWindow
import java.awt.Cursor
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

private val logger = KotlinLogging.logger {}

private const val WINDOW_TITLE = "comicripper $VERSION"

/** コミック一覧の背景（common.css の gray に対応）。 */
private val ListBackground = Color(0xFF808080)

/** ドラッグ中に表示する移動カーソル。 */
private val MoveCursorIcon = PointerIcon(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR))

/**
 * 選択の移動にスクロールを追従させるときのアニメーション。
 * 既定のスプリングは収束まで300ms超かかり、連続してキーやホイールを送ると追従が遅れて感じられるため、
 * 移動先が分かる程度に短く抑える。
 */
private val FollowSelectionScrollSpec = tween<Float>(durationMillis = 120, easing = FastOutSlowInEasing)

/**
 * アプリのルートウィンドウ。
 * コミック一覧を表示し、選択・キーボード/ホイールでの移動、詳細/カット画面の起動を行なう。
 * 開く各画面には自ウィンドウを owner として渡し、常に前面へ表示させる。
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun MainWindow(onCloseRequest: () -> Unit) {
    val setting: Setting = koinInject()
    val comicStorage: ComicStorage = koinInject()
    val appTaskScope: ApplicationScope = koinInject()

    val state = rememberPersistedWindowState(setting.mainWindow)

    val repos: ComicRepository = koinInject()
    val errorToast = rememberErrorToastState()
    val progress = rememberProgressOverlayState(onError = { title -> errorToast.show("${title}に失敗しました") })
    val nameAll = rememberTextAreaOverlayState()
    val comics = comicStorage.all

    var memoryText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            val runtime = Runtime.getRuntime()
            val free = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
            memoryText = "Free Memory: %dMB".format(free / 1024 / 1024)
        }
    }

    // 効果やフローの中で選択を見るときは、ここで読んだ値ではなく comicStorage.targetId を直接読むこと。
    // 長生きするラムダがこの値を掴むと、初回コンポジション時の選択に固定されてしまう。
    val selectedId = comicStorage.targetId
    val selectedComic = comics.firstOrNull { it.id == selectedId }

    fun selectComic(id: String?) {
        comicStorage.targetId = id
    }

    // 追加されたコミックを選択し、選択中が消えたら先頭へ移す。
    LaunchedEffect(Unit) {
        var previousIds = emptySet<String>()
        snapshotFlow { comicStorage.all }.collect { current ->
            runCatching {
                val currentIds = current.map { it.id }
                selectComic(selectionAfterChange(previousIds, currentIds, comicStorage.targetId))
                previousIds = currentIds.toSet()
            }.onFailure { logger.warn(it) { "storage collect failed" } }
        }
    }

    val headerAuthor = selectedComic?.author ?: ""
    val headerTitle = selectedComic?.title ?: ""

    fun moveSelection(direction: Int) {
        selectComic(selectionAfterMove(comics.map { it.id }, selectedId, direction))
    }

    fun openComic(comic: Comic?, owner: java.awt.Window?) {
        val target = comic ?: return
        appTaskScope.launch {
            runCatching {
                // isCoverFullLandscape は画像の読み込みを伴うため、EDT ではなくこのスコープで判定する。
                if (shouldUseCutter(target.coverFull, target.coverAlbum, target.isCoverFullLandscape)) {
                    showCutterWindow(target, owner)
                } else {
                    showDetailWindow(target, owner)
                }
            }.onFailure {
                logger.warn(it) { "openComic failed" }
                errorToast.show("画面を開けませんでした")
            }
        }
    }

    fun reScan() {
        appTaskScope.launch {
            runCatching {
                repos.reScanFiles()
                repos.saveStructure()
            }.onFailure {
                logger.warn(it) { "reScan failed" }
                errorToast.show("フォルダ再スキャンに失敗しました")
            }
        }
    }

    fun pagesToComic() {
        val target = selectedComic ?: return
        appTaskScope.launch {
            runCatching { repos.pagesToComic(target) }
                .onFailure {
                    logger.warn(it) { "pagesToComic failed" }
                    errorToast.show("pageの集約に失敗しました")
                }
        }
    }

    fun ocrAll() {
        progress.launchTask("OCRしています", "表紙画像から ISBN をまとめて読み取って著者名/作品名をサーチしてます") {
            // 1件の失敗が他のコミックを巻き込まないよう、子ジョブごとに保護し、失敗数を集計して通知する。
            val failedCount = AtomicInteger(0)
            val targets = comicStorage.all.filter { it.coverFull.isNullOrEmpty().not() }
            if (targets.isEmpty()) {
                errorToast.show("OCR一括: 対象のコミックがありません")
                return@launchTask
            }
            supervisorScope {
                targets
                    .map { comic ->
                        launch {
                            runCatching {
                                repos.ocrISBN(comic)?.let { (ocrAuthor, ocrTitle) ->
                                    comic.author = ocrAuthor
                                    comic.title = ocrTitle
                                }
                            }.onFailure {
                                logger.warn(it) { "ocrAll failed: ${comic.id}" }
                                failedCount.incrementAndGet()
                            }
                        }
                    }
                    .joinAll()
            }
            if (failedCount.get() > 0) {
                errorToast.show("OCR一括: ${failedCount.get()}/${targets.size}件失敗しました")
            }
        }
    }

    fun zipAll() {
        progress.launchTask("ZIPしています", "ページ数の多いコミックをまとめてZIP化しています") {
            val failedCount = AtomicInteger(0)
            val targets = comicStorage.all.filter { it.files.size > 3 }
            if (targets.isEmpty()) {
                errorToast.show("ZIP一括: 対象のコミックがありません")
                return@launchTask
            }
            supervisorScope {
                targets
                    .map { comic ->
                        launch {
                            runCatching { repos.zipComic(comic) }
                                .onFailure {
                                    logger.warn(it) { "zipAll failed: ${comic.id}" }
                                    failedCount.incrementAndGet()
                                }
                        }
                    }
                    .joinAll()
            }
            if (failedCount.get() > 0) {
                errorToast.show("ZIP一括: ${failedCount.get()}/${targets.size}件失敗しました")
            }
        }
    }

    fun extractEpub() {
        appTaskScope.launch {
            // ProcessBuilder.start() は起動失敗時に IOException を投げうる。
            runCatching {
                ProcessBuilder(
                    "cmd.exe", "/c", "start", "zsh.exe", "-c",
                    "epub2comic.py || read -k 1 '?エラーが発生しました。何かキーを押すと閉じます...'",
                )
                    .directory(File(setting.workDirectory))
                    .start()
            }.onFailure {
                logger.warn(it) { "extractEpub failed" }
                errorToast.show("epub展開の起動に失敗しました")
            }
        }
    }

    fun showNameAll() {
        nameAll.show(
            "一括命名",
            "1行を \"id\\t著者名\\t題名\" として編集してください",
            formatNameList(repos.getNameList()),
        ) { result ->
            repos.setNameList(parseNameList(result))
        }
    }

    val dragState = remember { ComicDragState() }

    fun onMerge(srcId: String, dstId: String) {
        val src = comicStorage[srcId] ?: return
        val dst = comicStorage[dstId] ?: return
        // 選択切り替えだけ先に同期で行なう。
        selectComic(dst.id)
        // merge は同期ディスク I/O（ImageIO.read + スケーリング）を伴うため EDT で直接呼ばない。
        appTaskScope.launch {
            runCatching {
                dst.merge(src)
                comicStorage.remove(src)
                repos.reScanFiles(dst)
            }.onFailure {
                logger.warn(it) { "merge failed" }
                errorToast.show("マージに失敗しました")
            }
        }
    }

    // キー入力から openComic を起動する際の owner（自ウィンドウ）。content 側で確定させる。
    var ownerWindow by remember { mutableStateOf<java.awt.Window?>(null) }

    val scrollState = rememberScrollState()
    var viewportHeightPx by remember { mutableStateOf(0) }

    // 選択中のカードの矩形。選択が移ると移動先のカードが再配置されて通知してくるので、
    // これ1つを見ていれば追従できる。
    var selectedCardBounds by remember { mutableStateOf<Rect?>(null) }

    // 選択カードが画面外なら見える位置までスクロールする（親=FlowRow 座標系はスクロール非依存）。
    LaunchedEffect(scrollState) {
        snapshotFlow { selectedCardBounds to viewportHeightPx }
            .collect { (bounds, viewport) ->
                runCatching {
                    followSelectionScrollTarget(bounds, viewport, scrollState.value, scrollState.maxValue)
                        ?.let { scrollState.animateScrollTo(it, FollowSelectionScrollSpec) }
                }.onFailure { logger.warn(it) { "scroll adjust failed" } }
            }
    }

    val windowTitle = if (selectedComic != null) {
        "$headerAuthor / $headerTitle - $WINDOW_TITLE"
    } else {
        WINDOW_TITLE
    }

    ComicRipperWindow(
        onCloseRequest = onCloseRequest,
        state = state,
        title = windowTitle,
        icon = rememberWindowIconPainter(),
        onPreviewKeyEvent = { event ->
            when {
                // 進捗中は全キー遮断。
                progress.isActive -> true
                // 一括命名のテキストエリアは複数行入力で矢印/Enterによるキャレット移動が必要なため、
                // ウィンドウレベルでキーを奪わない。
                nameAll.isActive -> false
                event.type != KeyEventType.KeyDown -> false
                else -> when (event.key) {
                    Key.DirectionRight, Key.DirectionDown -> {
                        moveSelection(1)
                        true
                    }
                    Key.DirectionLeft, Key.DirectionUp -> {
                        moveSelection(-1)
                        true
                    }
                    Key.Enter, Key.NumPadEnter -> {
                        openComic(selectedComic, ownerWindow)
                        true
                    }
                    else -> false
                }
            }
        },
    ) {
        BringToFrontOnFirstShow()
        LaunchedEffect(window) { ownerWindow = window }
        ComicRipperTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TopToolbar(
                            author = headerAuthor,
                            title = headerTitle,
                            onReScan = { reScan() },
                            onPagesToComic = { pagesToComic() },
                            onOcrAll = { ocrAll() },
                            onZipAll = { zipAll() },
                            onNameAll = { showNameAll() },
                            onEpubExtract = { extractEpub() },
                        )
                        Box(
                            modifier = Modifier
                                .weight(1.0f)
                                .fillMaxWidth()
                                .background(ListBackground)
                                .pointerHoverIcon(
                                    icon = if (dragState.draggingId != null) MoveCursorIcon else PointerIcon.Default,
                                    overrideDescendants = dragState.draggingId != null,
                                )
                                .onSizeChanged { viewportHeightPx = it.height }
                                .onPointerEvent(PointerEventType.Scroll, pass = PointerEventPass.Initial) { event ->
                                    // ドラッグ中はホイールを選択移動に使わず、スクロールへ委ねる。
                                    if (dragState.draggingId != null) {
                                        return@onPointerEvent
                                    }
                                    // verticalScroll より先に消費して、スクロールではなく選択移動に変換する。
                                    event.changes.forEach { it.consume() }
                                    runCatching {
                                        val deltaY = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                        when {
                                            deltaY > 0f -> moveSelection(1)
                                            deltaY < 0f -> moveSelection(-1)
                                        }
                                    }.onFailure { logger.warn(it) { "wheel select failed" } }
                                },
                        ) {
                            FlowRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(scrollState)
                                    .padding(2.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                comics.forEach { comic ->
                                    key(comic.id) {
                                        ComicCard(
                                            comic = comic,
                                            selected = comic.id == selectedId,
                                            isDragged = comic.id == dragState.draggingId,
                                            isDropTarget = comic.id == dragState.dropTargetId,
                                            dragState = dragState,
                                            onSelect = { selectComic(comic.id) },
                                            onOpen = { openComic(comic, window) },
                                            onBoundsInParent = { selectedCardBounds = it },
                                            onMerge = { src, dst -> onMerge(src, dst) },
                                        )
                                    }
                                }
                            }
                            VerticalScrollbar(
                                adapter = rememberScrollbarAdapter(scrollState),
                                modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                            )
                        }
                        BottomBar(
                            memoryText = memoryText,
                            onOpenSetting = {
                                ComposeWindowHost.show(key = "setting") { onClose ->
                                    SettingWindow(onCloseRequest = onClose, owner = window)
                                }
                            },
                        )
                    }
                    ProgressOverlay(progress)
                    TextAreaOverlay(nameAll)
                    ErrorToast(errorToast)
                }
            }
        }
    }
}

/**
 * 上部ツールバー。左に選択中の著者名・題名、右に操作ボタンを並べる。
 */
@Composable
private fun TopToolbar(
    author: String,
    title: String,
    onReScan: () -> Unit,
    onPagesToComic: () -> Unit,
    onOcrAll: () -> Unit,
    onZipAll: () -> Unit,
    onNameAll: () -> Unit,
    onEpubExtract: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(author, fontWeight = FontWeight.Bold)
        VerticalDivider(modifier = Modifier.height(16.dp))
        Text(title, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.weight(1.0f))
        CompactButton(onClick = onOcrAll) { Text("OCR") }
        CompactButton(onClick = onZipAll) { Text("ZIP作成＆全削除") }
        CompactButton(onClick = onPagesToComic) { Text("全pageを集約") }
        CompactButton(onClick = onReScan) { Text("フォルダ再スキャン") }
        CompactButton(onClick = onNameAll) { Text("一括命名") }
        CompactButton(onClick = onEpubExtract) { Text("epub展開") }
    }
}

/**
 * 下部バー。メモリ表示と設定ボタンを置く。
 */
@Composable
private fun BottomBar(memoryText: String, onOpenSetting: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.weight(1.0f))
        Text(memoryText)
        CompactButton(onClick = onOpenSetting) { Text("設定") }
    }
}

/**
 * 選択を [direction] のぶんだけ動かした結果の選択位置を返す。
 * 端を越える移動と、選択中のコミックが一覧に無い場合は、現在の選択をそのまま返す。
 */
internal fun selectionAfterMove(ids: List<String>, currentId: String?, direction: Int): String? {
    val index = ids.indexOfFirst { it == currentId }
    if (index < 0) {
        return currentId
    }
    return ids.getOrNull(index + direction) ?: currentId
}

/**
 * 一覧の中身が変わった後の選択位置を返す。
 * 追加があればいちばん後ろの追加分へ移し、選択中のコミックが消えていれば先頭へ移す。
 * どちらでもなければ現在の選択をそのまま返す。
 */
internal fun selectionAfterChange(
    previousIds: Set<String>,
    currentIds: List<String>,
    currentId: String?,
): String? {
    val added = currentIds.filterNot { it in previousIds }
    return when {
        added.isNotEmpty() -> added.last()
        currentId != null && currentId !in currentIds -> currentIds.firstOrNull()
        else -> currentId
    }
}

/**
 * 選択中のカードを表示範囲へ入れるためのスクロール位置を返す。
 * すでに見えている場合と、カードの矩形やビューポートの高さが未確定の場合は null を返す。
 *
 * [bounds] はスクロールしない親（FlowRow）の座標系で受け取るため、スクロール位置と直接比較できる。
 */
internal fun followSelectionScrollTarget(
    bounds: Rect?,
    viewportHeight: Int,
    currentScroll: Int,
    maxScroll: Int,
): Int? {
    if (bounds == null || viewportHeight <= 0) {
        return null
    }
    val top = bounds.top.roundToInt()
    val bottom = bounds.bottom.roundToInt()
    val target = when {
        top < currentScroll -> top
        bottom > currentScroll + viewportHeight -> bottom - viewportHeight
        else -> return null
    }.coerceIn(0, maxScroll)
    return target.takeIf { it != currentScroll }
}

/** 一括命名の編集用テキストを組み立てる。1行を「id・著者名・題名」のタブ区切りとする。 */
internal fun formatNameList(nameList: List<Triple<String, String, String>>): String =
    nameList.joinToString("\n") { (id, author, title) -> "$id\t$author\t$title" }

/** 一括命名の編集結果を読み取る。空行と、3列に分かれない行は捨てる。 */
internal fun parseNameList(text: String): List<Triple<String, String, String>> =
    text.lineSequence()
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            line.split("\t", limit = 3)
                .takeIf { it.size == 3 }
                ?.let { (id, author, title) -> Triple(id, author, title) }
        }
        .toList()

/**
 * 開く先が切り出し画面かどうかを返す。
 * 横長の表紙全体があってアルバム表紙がまだ無い状態は、表紙の切り出しが済んでいないことを表す。
 */
internal fun shouldUseCutter(coverFull: String?, coverAlbum: String?, isCoverFullLandscape: Boolean): Boolean =
    coverFull.isNullOrEmpty().not() && coverAlbum.isNullOrEmpty() && isCoverFullLandscape
