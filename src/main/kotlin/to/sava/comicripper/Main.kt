package to.sava.comicripper

import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.layout.BorderPane
import javafx.stage.Stage
import kotlinx.coroutines.*
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.java.KoinJavaComponent.get
import to.sava.comicripper.application.di.applicationModule
import to.sava.comicripper.controller.MainController
import to.sava.comicripper.domain.service.FileWatcher
import to.sava.comicripper.ext.loadFxml
import to.sava.comicripper.model.Setting
import to.sava.comicripper.repository.ComicRepository

class Main : Application(), CoroutineScope {
    private val job = Job()
    override val coroutineContext get() = Dispatchers.Main + job

    private lateinit var repos: ComicRepository
    private lateinit var fileWatcher: FileWatcher

    override fun start(primaryStage: Stage?) {
        checkNotNull(primaryStage)

        // Koin DIコンテナを初期化
        startKoin {
            modules(applicationModule)
        }
        
        // Koinから依存関係を取得
        repos = get(ComicRepository::class.java)
        fileWatcher = get(FileWatcher::class.java)

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
        stopKoin()
    }

    companion object {
        const val VERSION = "0.7.2"
    }
}
