package to.sava.comicripper

import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.ListChangeListener
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.Cursor
import javafx.scene.control.*
import javafx.scene.input.ClipboardContent
import javafx.scene.input.TransferMode
import javafx.scene.layout.BorderPane
import javafx.scene.layout.FlowPane
import javafx.scene.layout.VBox
import javafx.stage.Stage
import kotlinx.coroutines.*
import to.sava.comicripper.controller.ComicController
import to.sava.comicripper.ext.loadFxml
import to.sava.comicripper.ext.modalProgressDialog
import to.sava.comicripper.model.Comic
import to.sava.comicripper.repository.ComicRepository
import to.sava.comicripper.repository.ComicStorage
import tornadofx.*
import java.net.URL
import java.util.*


class MainController : Initializable, CoroutineScope {
    private val job = Job()
    override val coroutineContext get() = Dispatchers.Main + job

    @FXML
    private lateinit var comicList: FlowPane

    @FXML
    private lateinit var mainScene: BorderPane

    @FXML
    private lateinit var author: Label

    @FXML
    private lateinit var title: Label

    @FXML
    private lateinit var ocrIsbn: Button

    @FXML
    private lateinit var zip: Button

    @FXML
    private lateinit var pagesToComic: Button

    @FXML
    private lateinit var reload: Button

    private var stage: Stage? = null

    private val repos = ComicRepository()

    private val minWidthProperty = SimpleDoubleProperty(0.0)

    private val comicObjs = mutableMapOf<String, Pair<ComicController, VBox>>()

    private val selectedComicIdProperty = SimpleObjectProperty<String?>(null)
    val selectedComicId get() = selectedComicIdProperty.value

    override fun initialize(location: URL?, resources: ResourceBundle?) {
        selectedComicIdProperty.onChange { id ->
            ComicStorage[id]?.let { comic ->
                author.text = comic.author
                title.text = comic.title
            }
        }
        ocrIsbn.setOnAction {
            ComicStorage[selectedComicId]?.let { comic ->
                val modal = modalProgressDialog("OCRしています", "画像から ISBN を読み取って著者名/作品名をサーチしてます", requireNotNull(stage))
                modal.setOnCloseRequest {
                    job.cancel()
                }
                modal.show()
                launch(Dispatchers.IO) {
                    val (author_, title_) = repos.ocrISBN(comic)
                    withContext(Dispatchers.Main) {
                        author.textProperty().set(author_)
                        title.textProperty().set(title_)
                        modal.close()
                    }
                }
            }
        }
        zip.setOnAction {
            launch {
                val modal = modalProgressDialog("ZIPしています", "コミックをまとめてZIP化しています...", requireNotNull(stage))
                repos.zipAll()
                modal.close()
            }
        }
        pagesToComic.setOnAction {
            ComicStorage[selectedComicId]?.let { comic ->
                launch {
                    repos.pagesToComic(comic)
                }
            }
        }
        reload.setOnAction {
            launch {
                ComicStorage[selectedComicId]?.let {
                    repos.reScanFiles(it)
                } ?: repos.loadFiles()
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
        stage.minWidthProperty().bind(minWidthProperty)
    }

    private fun addComic(comic: Comic) {
        val (pane, controller) = loadFxml<VBox, ComicController>("comic.fxml")
        controller.apply {
            comicProperty.set(comic)
            addClickListener {
                selectComic(comic)
            }
        }
        pane.apply {
            minWidthProperty().onChange {
                minWidthProperty.value = 8.0 + (comicList.children.map { it.layoutBounds.width }.max() ?: 0.0)
            }
            setDragAndDrop(this, comic)
        }
        comicList.add(pane)
        comicObjs[comic.id] = Pair(controller, pane)

        if (comicObjs.size == 1) {
            selectComic(comic)
        }
    }

    private fun removeComic(comic: Comic) {
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
                        if (comic.mergeConflict(src)) {
                            alert(Alert.AlertType.CONFIRMATION, "コンフリクト", "このマージは情報が上書きされます．マージしてよろしいですか？") {
                                if (it.buttonData != ButtonBar.ButtonData.OK_DONE) {
                                    return@setOnDragDropped
                                }
                            }
                        }
                        comic.merge(src)
                        selectComic(comic)
                        event.isDropCompleted = true
                    }
                }
            }
            // ドラッグ完了
            setOnDragDone { event ->
                scene.cursor = Cursor.DEFAULT
                styleClass.remove("dragged")
                if (event.isAccepted) {
                    ComicStorage.delete(comic)
                }
            }
        }
    }

    private fun selectComic(comic: Comic) {
        val (_, pane) = comicObjs[comic.id] ?: return
        if ("selected" !in pane.styleClass) {
            comicList.children.forEach { it.styleClass.remove("selected") }
            pane.styleClass.add("selected")
        }
        selectedComicIdProperty.value = comic.id
    }
}