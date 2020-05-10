package to.sava.comicripper

import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleListProperty
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.ListChangeListener
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.layout.*
import javafx.stage.Stage
import to.sava.comicripper.controller.ComicController
import to.sava.comicripper.ext.loadFxml
import to.sava.comicripper.model.Comic
import tornadofx.*
import java.net.URL
import java.util.*

class MainController : Initializable {
    @FXML
    private lateinit var comicList: FlowPane

    @FXML
    private lateinit var pane: BorderPane

    @FXML
    private lateinit var author: TextField

    @FXML
    private lateinit var title: TextField

    @FXML
    private lateinit var button: Button

    @FXML
    private lateinit var label: Label

    val minWidthProperty = SimpleDoubleProperty(0.0)

    val comicObjs = mutableMapOf<Comic, Pair<ComicController, VBox>>()

    val comicListProperty = SimpleListProperty<Comic>(observableListOf())

    val selectedComicProperty = SimpleObjectProperty<Comic?>(null)

    override fun initialize(location: URL?, resources: ResourceBundle?) {
        selectedComicProperty.onChange {
            it?.let {
                author.text = it.author
                title.text = it.title
            }
        }
        author.textProperty().onChange {
            selectedComicProperty.value?.author = it ?: ""
        }
        title.textProperty().onChange {
            selectedComicProperty.value?.title = it ?: ""
        }
        button.setOnAction {
            println(1)
        }
        comicListProperty.addListener { change: ListChangeListener.Change<out Comic> ->
            while (change.next()) {
                when {
                    change.wasAdded() -> change.addedSubList.forEach { addComic(it) }
                    change.wasRemoved() -> change.removed.forEach { removeComic(it) }
                }
            }
        }
    }

    private var stage: Stage? = null
    fun initStage(stage: Stage) {
        this.stage = stage
        stage.minWidthProperty().bind(minWidthProperty)
    }

    fun addComic(comic: Comic) {
        val (pane, controller) = loadFxml<VBox, ComicController>("comic.fxml")
        controller.setComic(comic)
        controller.addClickListener {
            selectComic(comic)
        }
        pane.minWidthProperty().onChange {
            minWidthProperty.value = 8.0 + (comicList.children.map { it.layoutBounds.width }.max() ?: 0.0)
        }
        comicList.add(pane)
        comicObjs[comic] = Pair(controller, pane)
    }

    fun removeComic(comic: Comic) {
        comicObjs[comic]?.let {
            val (controller, pane) = it
            comicList.children.remove(pane)
            controller.destroy()
            comicObjs.remove(comic)
        }
    }

    fun selectComic(comic: Comic) {
        val (controller, pane) = comicObjs[comic] ?: return
        if ("selected" !in pane.styleClass) {
            comicList.children.forEach { it.styleClass.remove("selected") }
            pane.styleClass.add("selected")
        }
        selectedComicProperty.value = comic
    }
}