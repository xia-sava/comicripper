package to.sava.comicripper

import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.ListChangeListener
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.Cursor
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.input.ClipboardContent
import javafx.scene.input.TransferMode
import javafx.scene.layout.BorderPane
import javafx.scene.layout.FlowPane
import javafx.scene.layout.VBox
import javafx.stage.Stage
import to.sava.comicripper.controller.ComicController
import to.sava.comicripper.controller.CutterController
import to.sava.comicripper.ext.loadFxml
import to.sava.comicripper.model.Comic
import to.sava.comicripper.model.Setting
import to.sava.comicripper.repository.ComicStorage
import tornadofx.*
import java.net.URL
import java.util.*


class MainController : Initializable {
    @FXML
    private lateinit var comicList: FlowPane

    @FXML
    private lateinit var mainScene: BorderPane

    @FXML
    private lateinit var author: TextField

    @FXML
    private lateinit var title: TextField

    @FXML
    private lateinit var button: Button

    @FXML
    private lateinit var label: Label

    private var stage: Stage? = null

    private val minWidthProperty = SimpleDoubleProperty(0.0)

    private val comicObjs = mutableMapOf<String, Pair<ComicController, VBox>>()

    private val selectedComicProperty = SimpleObjectProperty<String?>(null)
    val selectedComicId get() = selectedComicProperty.value

    override fun initialize(location: URL?, resources: ResourceBundle?) {
        selectedComicProperty.onChange { id ->
            ComicStorage[id]?.let { comic ->
                author.text = comic.author
                title.text = comic.title
            }
        }
        author.textProperty().onChange {
            ComicStorage[selectedComicId]?.let { comic -> comic.author = it ?: "" }
        }
        title.textProperty().onChange {
            ComicStorage[selectedComicId]?.let { comic -> comic.title = it ?: "" }
        }
        button.setOnAction {
            println(1)
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
            addDoubleClickListener {
                executeComicAction(comic)
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
                label.text = comic.id
                if (event.gestureSource != this && event.dragboard.hasString()) {
                    event.acceptTransferModes(TransferMode.LINK);
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
                        var doMerge = true
                        if (comic.mergeConflict(src)) {
                            alert(Alert.AlertType.CONFIRMATION, "コンフリクト", "このマージは情報が上書きされます．マージしてよろしいですか？") {
                                if (it.buttonData != ButtonBar.ButtonData.OK_DONE) {
                                    alert(Alert.AlertType.WARNING, "a", "b")
                                    doMerge = false
                                }
                            }
                        }
                        if (doMerge) {
                            comic.merge(src)
                            event.isDropCompleted = true
                        }
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
        selectedComicProperty.value = comic.id
    }

    private fun executeComicAction(comic: Comic) {
        if (comic.coverAll == "") {
            alert(Alert.AlertType.ERROR, "フルカバーじゃないよ", "フルカバーがないコミックはカットできないのよ")
            return
        }
        val (cutterPane, cutterController) = loadFxml<BorderPane, CutterController>("cutter.fxml")
        Stage().apply {
            cutterController.initStage(this)
            scene = Scene(cutterPane)
            width = Setting.cutterWindowWidth
            height = Setting.cutterWindowHeight
            title = "${comic.title} ${comic.author} - comicripper 0.0.1"

            Setting.cutterWindowWidthProperty.bind(widthProperty())
            Setting.cutterWindowHeightProperty.bind(heightProperty())

            show()
        }
        cutterController.setComic(comic)
    }
}