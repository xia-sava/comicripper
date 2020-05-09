package to.sava.comicripper

import javafx.application.Application
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.scene.layout.BorderPane
import javafx.stage.Stage
import kotlinx.coroutines.*
import to.sava.comicripper.model.Setting
import to.sava.comicripper.repository.ComicRepository

class Main: Application(), CoroutineScope {
    override val coroutineContext = Dispatchers.Main + Job()
    private val comicRepos = ComicRepository()

    override fun start(primaryStage: Stage?) {
        checkNotNull(primaryStage)

        val loader = FXMLLoader(javaClass.getResource("main.fxml"))
        val rootPane = loader.load<BorderPane>()
        val controller = loader.getController<MainController>()

        launch {
            controller.updateComics(comicRepos.exampleListComic())
        }

        primaryStage.apply {
            scene = Scene(rootPane)
            width = Setting.windowWidth
            height = Setting.windowHeight
            show()
        }
    }

    override fun stop() {
        super.stop()
        coroutineContext.cancel()
    }
}