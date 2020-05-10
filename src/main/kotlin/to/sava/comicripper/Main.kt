package to.sava.comicripper

import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.layout.BorderPane
import javafx.stage.Stage
import kotlinx.coroutines.*
import to.sava.comicripper.ext.loadFxml
import to.sava.comicripper.model.Comic
import to.sava.comicripper.model.Setting
import to.sava.comicripper.repository.ComicRepository

class Main : Application(), CoroutineScope {
    private val job = Job()
    override val coroutineContext get() = Dispatchers.Main + job

    private val comicRepos = ComicRepository()

    private val comics = mutableListOf<Comic>()

    override fun start(primaryStage: Stage?) {
        checkNotNull(primaryStage)
        val (mainPane, mainController) = loadFxml<BorderPane, MainController>("main.fxml")

        mainController.initStage(primaryStage)
        primaryStage.apply {
            scene = Scene(mainPane)
            width = Setting.windowWidth
            height = Setting.windowHeight
            title = "comicripper 0.0.1"

            show()
        }

        launch {
            comicRepos.exampleListComic().forEach { comic ->
                comics.add(comic)
                mainController.comicListProperty.add(comic)
            }
        }
    }

    override fun stop() {
        super.stop()
        job.cancel()
    }
}