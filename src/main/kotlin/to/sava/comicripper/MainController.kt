package to.sava.comicripper

import javafx.beans.property.SimpleDoubleProperty
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.*
import javafx.stage.Stage
import kotlinx.coroutines.*
import to.sava.comicripper.controller.ComicController
import to.sava.comicripper.ext.loadFxml
import to.sava.comicripper.model.Comic
import to.sava.comicripper.repository.ComicRepository
import tornadofx.*
import java.net.URL
import java.util.*
import kotlin.math.max

class MainController: Initializable, CoroutineScope {
    private val job = Job()
    override val coroutineContext get() = Dispatchers.Main + job
    private val comicRepos = ComicRepository()

    @FXML
    private lateinit var comicList: FlowPane

    @FXML
    private lateinit var pane: BorderPane

    @FXML
    private lateinit var button: Button

    @FXML
    private lateinit var label: Label

    private val minWidthProperty = SimpleDoubleProperty(0.0)

    override fun initialize(location: URL?, resources: ResourceBundle?) {
        button.setOnAction {
            label.text = "stage:${stage?.width} pane:${pane.prefWidth} cLmin:${comicList.minWidth}"
        }

        launch {
            val comics = comicRepos.exampleListComic()
            updateComics(comics)
        }
    }

    fun stop() {
        job.cancel()
    }

    private var stage: Stage? = null
    fun initStage(stage: Stage) {
        this.stage = stage
        stage.minWidthProperty().bind(minWidthProperty)
    }

    private fun updateComics(comics: List<Comic>) {
        comicList.clear()
        comics.forEach {
            val (pane, controller) = loadFxml<VBox, ComicController>("comic.fxml")
            controller.apply { updateComic(it) }
            pane.minWidthProperty().onChange {
                minWidthProperty.value = max(minWidthProperty.value, it)
            }
            comicList.add(pane)
        }
    }
}