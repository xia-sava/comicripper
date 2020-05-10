package to.sava.comicripper

import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.layout.BorderPane
import javafx.stage.Stage
import kotlinx.coroutines.*
import to.sava.comicripper.ext.loadFxml
import to.sava.comicripper.model.Setting

class Main: Application(), CoroutineScope {
    private val job = Job()
    override val coroutineContext get() = Dispatchers.Main + job

    override fun start(primaryStage: Stage?) {
        checkNotNull(primaryStage)
        val (rootPane, rootController) = loadFxml<BorderPane, MainController>("main.fxml")

        rootController.initStage(primaryStage)
        primaryStage.apply {
            scene = Scene(rootPane)
            width = Setting.windowWidth
            height = Setting.windowHeight
            show()
        }
    }

    override fun stop() {
        super.stop()
        job.cancel()
    }
}