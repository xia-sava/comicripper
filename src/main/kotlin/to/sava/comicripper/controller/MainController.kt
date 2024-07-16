package to.sava.comicripper.controller

import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.ListChangeListener
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.Cursor
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.input.ClipboardContent
import javafx.scene.input.KeyCode
import javafx.scene.input.TransferMode
import javafx.scene.layout.BorderPane
import javafx.scene.layout.FlowPane
import javafx.scene.layout.VBox
import javafx.stage.Stage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import to.sava.comicripper.Main
import to.sava.comicripper.ext.*
import to.sava.comicripper.model.Comic
import to.sava.comicripper.model.Setting
import to.sava.comicripper.repository.ComicRepository
import to.sava.comicripper.repository.ComicStorage
import tornadofx.add
import tornadofx.onChange
import java.net.URL
import java.util.*
import kotlin.collections.set


private const val WINDOW_TITLE = "comicripper ${Main.VERSION}"

class MainController : Initializable, CoroutineScope {
    private val job = Job()
    override val coroutineContext get() = Dispatchers.Main + job

    @Suppress("unused")
    @FXML
    private lateinit var mainScene: BorderPane

    @FXML
    private lateinit var comicList: FlowPane

    @FXML
    private lateinit var author: Label

    @FXML
    private lateinit var title: Label

    @FXML
    private lateinit var notifyLabel: Label

    @FXML
    private lateinit var ocrIsbn: Button

    @FXML
    private lateinit var zip: Button

    @FXML
    private lateinit var pagesToComic: Button

    @FXML
    private lateinit var reload: Button

    @FXML
    private lateinit var nameAll: Button

    @FXML
    private lateinit var scrollPane: ScrollPane

    @Suppress("unused")
    @FXML
    private lateinit var statusBar: Label

    @FXML
    private lateinit var setting: Button

    private var stage: Stage? = null

    private val repos = ComicRepository()

    private val minWidthProperty = SimpleDoubleProperty(0.0)

    private val comicObjs = mutableMapOf<String, Pair<ComicController, VBox>>()

    private val selectedComicIdProperty = SimpleObjectProperty<String?>(null)
    val selectedComicId get() = selectedComicIdProperty.value

    override fun initialize(location: URL?, resources: ResourceBundle?) {
        ocrIsbn.setOnAction {
            modalProgressDialog(
                "OCRしています",
                "画像から ISBN を読み取って著者名/作品名をサーチしてます",
                stage
            ) { job ->
                ComicStorage.all.filter { it.coverAll.isNullOrEmpty().not() }.map { comic ->
                    launch(Dispatchers.IO + job) {
                        repos.ocrISBN(comic)?.let {
                            comic.author = it.first
                            comic.title = it.second
                        }
                    }
                }
            }
        }
        zip.setOnAction {
            modalProgressDialog(
                "ZIPしています",
                "コミックをまとめてZIP化しています",
                stage
            ) {
                ComicStorage.all.filter { it.files.size > 3 }.map { comic ->
                    launch(Dispatchers.IO + job) {
                        repos.zipComic(comic)
                    }
                }
            }
        }
        pagesToComic.setOnAction {
            ComicStorage[selectedComicId]?.let { comic ->
                launch(Dispatchers.IO + job) {
                    repos.pagesToComic(comic)
                }
            }
        }
        reload.setOnAction {
            launch(Dispatchers.IO + job) {
                repos.reScanFiles()
                repos.saveStructure()
            }
        }

        nameAll.setOnAction {
            modalTextAreaDialog(
                "まとめて名前をセットします",
                "全てのコミックの著者名/作品名をセットしてください",
                stage,
                text = repos.getNameList()
                    .joinToString(separator = "\n") { triple ->
                        triple.toList().joinToString(separator = "\t")
                    } + "\n",
                result = { input ->
                    input.lines().forEach { line ->
                        val record = line.split("\t")
                        if (record.size == 3) {
                            ComicStorage[record[0]]?.let { comic ->
                                comic.author = record[1]
                                comic.title = record[2]
                            }
                        }
                    }
                },
            )
        }

        scrollPane.content.setOnScroll {
            when {
                it.deltaY > 0 -> moveComicFocus(-1)
                it.deltaY < 0 -> moveComicFocus(+1)
            }
            it.consume()
        }

        setting.setOnAction {
            stage?.let { SettingController.launchStage(it) }
        }

        launch(Dispatchers.Default + job) {
            while (true) {
                delay(5_000)
                val runtime = Runtime.getRuntime()
                val free = runtime.maxMemory() - (runtime.totalMemory() - runtime.freeMemory())
                withContext(Dispatchers.Main + job) {
                    notifyLabel.text = "Free Memory: %dMB".format(free / 1024 / 1024)
                }
            }
        }

        ComicStorage.property.onChange { change: ListChangeListener.Change<out Comic> ->
            while (change.next()) {
                when {
                    change.wasAdded() -> change.addedSubList.forEach { addComic(it) }
                    change.wasRemoved() -> change.removed.forEach { removeComic(it) }
                }
            }
        }
    }

    fun initStage(stage: Stage) {
        this.stage = stage
        stage.apply {
            setWindowIcon()
            width = Setting.mainWindowWidth
            height = Setting.mainWindowHeight
            if (Setting.mainWindowPosX >= 0.0) {
                x = Setting.mainWindowPosX
            }
            if (Setting.mainWindowPosY >= 0.0) {
                y = Setting.mainWindowPosY
            }
            title = WINDOW_TITLE

            Setting.mainWindowWidthProperty.bind(widthProperty())
            Setting.mainWindowHeightProperty.bind(heightProperty())
            Setting.mainWindowPosXProperty.bind(xProperty())
            Setting.mainWindowPosYProperty.bind(yProperty())
        }
        stage.minWidthProperty().bind(minWidthProperty)
    }

    private fun addComic(comic: Comic) = launch {
        val (pane, controller) = loadFxml<VBox, ComicController>("comic.fxml")
        controller.apply {
            comicProperty.set(comic)
            stage?.let { initStage(it) }
            addClickListener {
                selectComic(comic)
            }
            addKeyPressedListener {
                keyboardControl(comic, it)
            }
        }
        pane.apply {
            minWidthProperty().onChange {
                minWidthProperty.value =
                    8.0 + (comicList.children.maxOfOrNull { it.layoutBounds.width } ?: 0.0)
            }
            setDragAndDrop(this, comic)
        }
        comicList.add(pane)
        comicObjs[comic.id] = Pair(controller, pane)

        if (comicObjs.size == 1) {
            selectComic(comic)
        }
    }

    private fun removeComic(comic: Comic) = launch {
        if (selectedComicId == comic.id) {
            selectComic(null)
        }
        comicObjs[comic.id]?.let {
            val (controller, pane) = it
            comicList.children.remove(pane)
            controller.destroy()
            comicObjs.remove(comic.id)
        }
    }

    private fun setDragAndDrop(pane: VBox, comic: Comic) {
        pane.apply {
            // ドラッグ開始
            setOnDragDetected {
                startDragAndDrop(TransferMode.LINK)
                    .setContent(ClipboardContent().apply { putString(comic.id) })
                styleClass.add("dragged")
                scene.cursor = Cursor.CLOSED_HAND
                startFullDrag()
            }
            // ドラッグ相手が hover する前に受け入れ可能かどうかを確認する
            setOnDragOver { event ->
                if (event.gestureSource != this && event.dragboard.hasString()) {
                    event.acceptTransferModes(TransferMode.LINK)
                }
            }
            // ドラッグ相手が hover した状態
            setOnDragEntered { event ->
                if (event.gestureSource != this && event.dragboard.hasString()) {
                    styleClass.add("dragover")
                }
            }
            // ドラッグ相手が mouseout した状態
            setOnDragExited {
                styleClass.remove("dragover")
            }
            // ドラッグ相手を受け取る処理
            setOnDragDropped { event ->
                event.isDropCompleted = false
                if (event.dragboard.hasString()) {
                    ComicStorage[event.dragboard.string]?.let { src ->
                        comic.merge(src)
                        selectComic(comic)
                        launch {
                            repos.reScanFiles(comic)
                        }
                        event.isDropCompleted = true
                    }
                }
            }
            // ドラッグ完了
            setOnDragDone { event ->
                scene.cursor = Cursor.DEFAULT
                styleClass.remove("dragged")
                if (event.isAccepted) {
                    ComicStorage.remove(comic)
                }
            }
        }
    }

    private fun selectComic(comic: Comic?) {
        comicObjs[selectedComicId]?.first?.comicProperty?.value?.removeListener(::setWindowTitle)
        if (comic == null) {
            selectedComicIdProperty.value = null
            return
        }
        val (controller, pane) = comicObjs[comic.id] ?: return
        pane.children.firstOrNull()?.requestFocus()
        if ("selected" !in pane.styleClass) {
            comicList.children.forEach { it.styleClass.remove("selected") }
            pane.styleClass.add("selected")
        }
        selectedComicIdProperty.value = comic.id
        controller.comicProperty.value?.addListener(::setWindowTitle)
        setWindowTitle()

        // 選択コミックが画面内に入るようにスクロールする
        val paneTop = pane.layoutY // 対象位置 px
        val paneBottom = pane.layoutY + pane.height // 対象下端位置 px
        val screenHeight = scrollPane.viewportBounds.height
        val scrollLength = comicList.height - screenHeight
        if (paneTop < scrollLength * scrollPane.vvalue) {
            scrollPane.vvalue = paneTop / scrollLength
        }
        if ((scrollLength * scrollPane.vvalue + screenHeight) < paneBottom) {
            scrollPane.vvalue = (paneBottom - screenHeight) / scrollLength
        }
    }

    private fun setWindowTitle(@Suppress("UNUSED_PARAMETER") target: Comic? = null) {
        launch {
            comicObjs[selectedComicId]?.first?.comicProperty?.value?.let { comic ->
                author.text = comic.author
                title.text = comic.title
                stage?.title = if (title.text.isNotEmpty() && author.text.isNotEmpty())
                    "${author.text} / ${title.text} - $WINDOW_TITLE" else WINDOW_TITLE
            }
        }
    }

    private fun keyboardControl(@Suppress("UNUSED_PARAMETER") comic: Comic, code: KeyCode) {
        when (code) {
            KeyCode.RIGHT, KeyCode.DOWN -> moveComicFocus(1)
            KeyCode.LEFT, KeyCode.UP -> moveComicFocus(-1)
            else -> {
            }
        }
    }

    private fun moveComicFocus(dir: Int) {
        val currentIndex = ComicStorage.all.indexOfFirst { it.id == selectedComicId }
        if (currentIndex == -1) {
            return
        }
        if (currentIndex + dir !in ComicStorage.all.indices) {
            return
        }
        selectComic(ComicStorage.all[currentIndex + dir])
    }
}
