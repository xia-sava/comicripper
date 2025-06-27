package to.sava.comicripper

import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.layout.BorderPane
import javafx.stage.Stage
import kotlinx.coroutines.*
import to.sava.comicripper.controller.MainController
import to.sava.comicripper.domain.service.FileWatcher
import to.sava.comicripper.ext.loadFxml
import to.sava.comicripper.infrastructure.service.JNotifyFileWatcher
import to.sava.comicripper.model.Setting
import to.sava.comicripper.repository.ComicRepository

class Main : Application(), CoroutineScope {
    private val job = Job()
    override val coroutineContext get() = Dispatchers.Main + job

    private val repos = ComicRepository()
    private val fileWatcher: FileWatcher = JNotifyFileWatcher()

    override fun start(primaryStage: Stage?) {
        checkNotNull(primaryStage)

        Setting.load()

        val (mainPane, mainController) = loadFxml<BorderPane, MainController>("main.fxml")
        primaryStage.apply {
            mainController.initStage(this)
            scene = Scene(mainPane)
            show()
        }

        repos.loadStructure()
        repos.reScanFiles()

        launch(Dispatchers.IO + job) {
            while (true) {
                delay(30_000)
                repos.saveStructure()
            }
        }

        fileWatcher.start(
            Setting.workDirectory,
            onFilesAdded = { filenames ->
                repos.addFiles(filenames)
            },
            onFilesDeleted = { filenames ->
                repos.removeFiles(filenames)
            }
        )
    }

    override fun stop() {
        super.stop()
        fileWatcher.stop()
        job.cancel()
        Setting.save()
        repos.saveStructure()
    }

    companion object {
        const val VERSION = "0.6.6"
    }
}
