package to.sava.comicripper.ui.main

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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
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
import to.sava.comicripper.ui.ProgressOverlay
import to.sava.comicripper.ui.TextAreaOverlay
import to.sava.comicripper.ui.cutter.showCutterWindow
import to.sava.comicripper.ui.detail.showDetailWindow
import to.sava.comicripper.ui.rememberProgressOverlayState
import to.sava.comicripper.ui.rememberTextAreaOverlayState
import to.sava.comicripper.ui.rememberWindowIconPainter
import to.sava.comicripper.ui.setting.SettingWindow
import java.awt.Cursor
import java.io.File
import kotlin.math.roundToInt

private val logger = KotlinLogging.logger {}

private const val WINDOW_TITLE = "comicripper $VERSION"

/** コミック一覧の背景（common.css の gray に対応）。 */
private val ListBackground = Color(0xFF808080)

/** ドラッグ中に表示する移動カーソル。 */
private val MoveCursorIcon = PointerIcon(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR))

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

    val state = rememberWindowState(
        size = DpSize(setting.mainWindowWidth.dp, setting.mainWindowHeight.dp),
        position = if (setting.mainWindowPosX >= 0.0) {
            WindowPosition.Absolute(setting.mainWindowPosX.dp, setting.mainWindowPosY.dp)
        } else {
            WindowPosition.PlatformDefault
        },
    )
    LaunchedEffect(state) {
        snapshotFlow { state.size }.collect { size ->
            setting.mainWindowWidth = size.width.value.toDouble()
            setting.mainWindowHeight = size.height.value.toDouble()
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.position }.collect { position ->
            if (position is WindowPosition.Absolute) {
                setting.mainWindowPosX = position.x.value.toDouble()
                setting.mainWindowPosY = position.y.value.toDouble()
            }
        }
    }

    val repos: ComicRepository = koinInject()
    val progress = rememberProgressOverlayState()
    val nameAll = rememberTextAreaOverlayState()
    val comics by comicStorage.storage.collectAsState()

    var memoryText by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            val runtime = Runtime.getRuntime()
            val free = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
            memoryText = "Free Memory: %dMB".format(free / 1024 / 1024)
        }
    }

    var selectedId by remember { mutableStateOf(comicStorage.targetId) }
    val selectedComic = comics.firstOrNull { it.id == selectedId }

    fun selectComic(id: String?) {
        selectedId = id
        comicStorage.targetId = id
    }

    // 追加されたコミックを選択し、選択中が消えたら先頭へ移す。
    LaunchedEffect(Unit) {
        var previousIds = emptySet<String>()
        comicStorage.storage.collect { current ->
            runCatching {
                val added = current.filter { it.id !in previousIds }
                when {
                    added.isNotEmpty() -> selectComic(added.last().id)
                    selectedId != null && current.none { it.id == selectedId } ->
                        selectComic(current.firstOrNull()?.id)
                }
                previousIds = current.map { it.id }.toSet()
            }.onFailure { logger.warn(it) { "storage collect failed" } }
        }
    }

    var headerAuthor by remember { mutableStateOf("") }
    var headerTitle by remember { mutableStateOf("") }
    LaunchedEffect(selectedComic) {
        val comic = selectedComic
        if (comic == null) {
            headerAuthor = ""
            headerTitle = ""
            return@LaunchedEffect
        }
        headerAuthor = comic.author
        headerTitle = comic.title
        comic.changeFlow.collect {
            headerAuthor = comic.author
            headerTitle = comic.title
        }
    }

    fun moveSelection(direction: Int) {
        val index = comics.indexOfFirst { it.id == selectedId }
        if (index < 0) {
            return
        }
        val next = index + direction
        if (next in comics.indices) {
            selectComic(comics[next].id)
        }
    }

    fun openComic(comic: Comic?, owner: java.awt.Window?) {
        val target = comic ?: return
        appTaskScope.launch {
            runCatching {
                val useCutter = target.coverFull.isNullOrEmpty().not() &&
                    target.coverAlbum.isNullOrEmpty() &&
                    target.isCoverFullLandscape
                if (useCutter) {
                    showCutterWindow(target, owner)
                } else {
                    showDetailWindow(target, owner)
                }
            }.onFailure { logger.warn(it) { "openComic failed" } }
        }
    }

    fun reScan() {
        appTaskScope.launch {
            runCatching {
                repos.reScanFiles()
                repos.saveStructure()
            }.onFailure { logger.warn(it) { "reScan failed" } }
        }
    }

    fun pagesToComic() {
        val target = selectedComic ?: return
        appTaskScope.launch {
            runCatching { repos.pagesToComic(target) }
                .onFailure { logger.warn(it) { "pagesToComic failed" } }
        }
    }

    fun ocrAll() {
        progress.launchTask("OCRしています", "表紙画像から ISBN をまとめて読み取って著者名/作品名をサーチしてます") {
            // 1件の失敗が他のコミックを巻き込まないよう、子ジョブごとに保護する。
            supervisorScope {
                comicStorage.all
                    .filter { it.coverFull.isNullOrEmpty().not() }
                    .map { comic ->
                        launch {
                            runCatching {
                                repos.ocrISBN(comic)?.let { (ocrAuthor, ocrTitle) ->
                                    comic.author = ocrAuthor
                                    comic.title = ocrTitle
                                }
                            }.onFailure { logger.warn(it) { "ocrAll failed: ${comic.id}" } }
                        }
                    }
                    .joinAll()
            }
        }
    }

    fun zipAll() {
        progress.launchTask("ZIPしています", "ページ数の多いコミックをまとめてZIP化しています") {
            supervisorScope {
                comicStorage.all
                    .filter { it.files.size > 3 }
                    .map { comic ->
                        launch {
                            runCatching { repos.zipComic(comic) }
                                .onFailure { logger.warn(it) { "zipAll failed: ${comic.id}" } }
                        }
                    }
                    .joinAll()
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
            }.onFailure { logger.warn(it) { "extractEpub failed" } }
        }
    }

    fun showNameAll() {
        val text = repos.getNameList().joinToString("\n") { (id, author, title) -> "$id\t$author\t$title" }
        nameAll.show("一括命名", "1行を \"id\\t著者名\\t題名\" として編集してください", text) { result ->
            val nameList = result.lineSequence()
                .filter { it.isNotBlank() }
                .mapNotNull { line ->
                    val parts = line.split("\t", limit = 3)
                    if (parts.size == 3) Triple(parts[0], parts[1], parts[2]) else null
                }
                .toList()
            repos.setNameList(nameList)
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
            }.onFailure { logger.warn(it) { "merge failed" } }
        }
    }

    // キー入力から openComic を起動する際の owner（自ウィンドウ）。content 側で確定させる。
    var ownerWindow by remember { mutableStateOf<java.awt.Window?>(null) }

    val scrollState = rememberScrollState()
    var viewportHeightPx by remember { mutableStateOf(0) }
    val cardBounds = remember { mutableStateMapOf<String, Rect>() }

    // 選択カードが画面外なら見える位置までスクロールする（親=FlowRow 座標系はスクロール非依存）。
    LaunchedEffect(scrollState) {
        snapshotFlow { Triple(selectedId, selectedId?.let { cardBounds[it] }, viewportHeightPx) }
            .collect { (_, bounds, viewport) ->
                runCatching {
                    if (bounds == null || viewport <= 0) {
                        return@runCatching
                    }
                    val current = scrollState.value
                    val maxScroll = scrollState.maxValue
                    val top = bounds.top.roundToInt()
                    val bottom = bounds.bottom.roundToInt()
                    val target = when {
                        top < current -> top
                        bottom > current + viewport -> bottom - viewport
                        else -> return@runCatching
                    }.coerceIn(0, maxScroll)
                    if (target != current) {
                        scrollState.animateScrollTo(target)
                    }
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
                                            onBoundsInParent = { cardBounds[comic.id] = it },
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
