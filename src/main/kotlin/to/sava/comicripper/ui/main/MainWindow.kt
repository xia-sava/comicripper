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
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import to.sava.comicripper.VERSION
import to.sava.comicripper.model.Comic
import to.sava.comicripper.model.Setting
import to.sava.comicripper.repository.ComicRepository
import to.sava.comicripper.repository.ComicStorage
import to.sava.comicripper.ui.BringToFrontOnFirstShow
import to.sava.comicripper.ui.ComicRipperTheme
import to.sava.comicripper.ui.ComicRipperWindow
import to.sava.comicripper.ui.CompactButton
import to.sava.comicripper.ui.ComposeWindowHost
import to.sava.comicripper.ui.cutter.showCutterWindow
import to.sava.comicripper.ui.detail.showDetailWindow
import to.sava.comicripper.ui.rememberWindowIconPainter
import to.sava.comicripper.ui.setting.SettingWindow
import kotlin.math.roundToInt

private const val WINDOW_TITLE = "comicripper $VERSION"

/** コミック一覧の背景（common.css の gray に対応）。 */
private val ListBackground = Color(0xFF808080)

/**
 * ウィンドウを閉じた後も完走させたい処理の実行スコープ
 * （画面起動時の画像判定、再スキャン、page 集約など）。
 */
private val appTaskScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

/**
 * アプリのルートウィンドウ。
 * コミック一覧を表示し、選択・キーボード/ホイールでの移動、詳細/カット画面の起動を行なう。
 * 開く各画面には自ウィンドウを owner として渡し、常に前面へ表示させる。
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun MainWindow(onCloseRequest: () -> Unit) {
    val state = rememberWindowState(
        size = DpSize(Setting.mainWindowWidth.dp, Setting.mainWindowHeight.dp),
        position = if (Setting.mainWindowPosX >= 0.0) {
            WindowPosition.Absolute(Setting.mainWindowPosX.dp, Setting.mainWindowPosY.dp)
        } else {
            WindowPosition.PlatformDefault
        },
    )
    LaunchedEffect(state) {
        snapshotFlow { state.size }.collect { size ->
            Setting.mainWindowWidth = size.width.value.toDouble()
            Setting.mainWindowHeight = size.height.value.toDouble()
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.position }.collect { position ->
            if (position is WindowPosition.Absolute) {
                Setting.mainWindowPosX = position.x.value.toDouble()
                Setting.mainWindowPosY = position.y.value.toDouble()
            }
        }
    }

    val repos = remember { ComicRepository() }
    val comics by ComicStorage.storage.collectAsState()

    var selectedId by remember { mutableStateOf(ComicStorage.targetId) }
    val selectedComic = comics.firstOrNull { it.id == selectedId }

    fun selectComic(id: String?) {
        selectedId = id
        ComicStorage.targetId = id
    }

    // 追加されたコミックを選択し、選択中が消えたら先頭へ移す。
    LaunchedEffect(Unit) {
        var previousIds = emptySet<String>()
        ComicStorage.storage.collect { current ->
            runCatching {
                val added = current.filter { it.id !in previousIds }
                when {
                    added.isNotEmpty() -> selectComic(added.last().id)
                    selectedId != null && current.none { it.id == selectedId } ->
                        selectComic(current.firstOrNull()?.id)
                }
                previousIds = current.map { it.id }.toSet()
            }.onFailure { println("storage collect failed: ${it.javaClass.simpleName}: ${it.message}") }
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
            }.onFailure { println("openComic failed: ${it.javaClass.simpleName}: ${it.message}") }
        }
    }

    fun reScan() {
        appTaskScope.launch {
            runCatching {
                repos.reScanFiles()
                repos.saveStructure()
            }.onFailure { println("reScan failed: ${it.javaClass.simpleName}: ${it.message}") }
        }
    }

    fun pagesToComic() {
        val target = selectedComic ?: return
        appTaskScope.launch {
            runCatching { repos.pagesToComic(target) }
                .onFailure { println("pagesToComic failed: ${it.javaClass.simpleName}: ${it.message}") }
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
                }.onFailure { println("scroll adjust failed: ${it.javaClass.simpleName}: ${it.message}") }
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
            if (event.type != KeyEventType.KeyDown) {
                false
            } else {
                when (event.key) {
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
                Column(modifier = Modifier.fillMaxSize()) {
                    TopToolbar(
                        author = headerAuthor,
                        title = headerTitle,
                        onReScan = { reScan() },
                        onPagesToComic = { pagesToComic() },
                    )
                    Box(
                        modifier = Modifier
                            .weight(1.0f)
                            .fillMaxWidth()
                            .background(ListBackground)
                            .onSizeChanged { viewportHeightPx = it.height }
                            .onPointerEvent(PointerEventType.Scroll, pass = PointerEventPass.Initial) { event ->
                                // verticalScroll より先に消費して、スクロールではなく選択移動に変換する。
                                event.changes.forEach { it.consume() }
                                runCatching {
                                    val deltaY = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                    when {
                                        deltaY > 0f -> moveSelection(1)
                                        deltaY < 0f -> moveSelection(-1)
                                    }
                                }.onFailure { println("wheel select failed: ${it.message}") }
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
                                        onSelect = { selectComic(comic.id) },
                                        onOpen = { openComic(comic, window) },
                                        onBoundsInParent = { cardBounds[comic.id] = it },
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
                        onOpenSetting = {
                            ComposeWindowHost.show(key = "setting") { onClose ->
                                SettingWindow(onCloseRequest = onClose, owner = window)
                            }
                        },
                    )
                }
            }
        }
    }
}

/**
 * 上部ツールバー。左に選択中の著者名・題名、右に操作ボタンを並べる。
 * OCR・ZIP作成・一括命名・epub展開は配置のみ（後続の作業単位で配線する）。
 */
@Composable
private fun TopToolbar(
    author: String,
    title: String,
    onReScan: () -> Unit,
    onPagesToComic: () -> Unit,
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
        CompactButton(onClick = {}, enabled = false) { Text("OCR") }
        CompactButton(onClick = {}, enabled = false) { Text("ZIP作成＆全削除") }
        CompactButton(onClick = onPagesToComic) { Text("全pageを集約") }
        CompactButton(onClick = onReScan) { Text("フォルダ再スキャン") }
        CompactButton(onClick = {}, enabled = false) { Text("一括命名") }
        CompactButton(onClick = {}, enabled = false) { Text("epub展開") }
    }
}

/**
 * 下部バー。メモリ表示（値の更新は後続の作業単位）と設定ボタンを置く。
 */
@Composable
private fun BottomBar(onOpenSetting: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.weight(1.0f))
        Text("")
        CompactButton(onClick = onOpenSetting) { Text("設定") }
    }
}
