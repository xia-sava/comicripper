package to.sava.comicripper

import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.layout.BorderPane
import javafx.stage.Stage
import kotlinx.coroutines.*
import net.contentobjects.jnotify.win32.JNotify_win32
import to.sava.comicripper.controller.MainController
import to.sava.comicripper.ext.loadFxml
import to.sava.comicripper.model.Comic
import to.sava.comicripper.model.Setting
import to.sava.comicripper.repository.ComicRepository
import to.sava.comicripper.repository.ComicStorage

class Main : Application(), CoroutineScope {
    private val job = Job()
    override val coroutineContext get() = Dispatchers.Main + job

    private val repos = ComicRepository()

    private val fileCreatedQueue = mutableListOf<String>()
    private val fileDeletedQueue = mutableListOf<String>()

    override fun start(primaryStage: Stage?) {
        checkNotNull(primaryStage)
        val (mainPane, mainController) = loadFxml<BorderPane, MainController>("main.fxml")

        Setting.load()

        repos.loadStructure()
        repos.reScanFiles()

        mainController.initStage(primaryStage)
        primaryStage.apply {
            scene = Scene(mainPane)
            width = Setting.mainWindowWidth
            height = Setting.mainWindowHeight
            title = "comicripper 0.0.1"

            Setting.mainWindowWidthProperty.bind(primaryStage.widthProperty())
            Setting.mainWindowHeightProperty.bind(primaryStage.heightProperty())

            show()
        }

        launch(Dispatchers.IO + job) {
            while (true) {
                delay(30_000)
                repos.saveStructure()
            }
        }

        try {
            JNotify_win32.addWatch(
                Setting.workDirectory,
                JNotify_win32.FILE_ACTION_ADDED.toLong() or JNotify_win32.FILE_ACTION_REMOVED.toLong(),
                false
            )
            JNotify_win32.setNotifyListener { _, action, _, filePath ->
                when (action) {
                    JNotify_win32.FILE_ACTION_ADDED -> synchronized(fileCreatedQueue) {
                        if (filePath.matches(Comic.TARGET_REGEX)) {
                            println("JNotify_win32: added $filePath")
                            fileCreatedQueue.add(filePath)
                        }
                    }
                    JNotify_win32.FILE_ACTION_REMOVED -> synchronized(fileDeletedQueue) {
                        if (filePath.matches(Comic.TARGET_REGEX)) {
                            println("JNotify_win32: deleted $filePath")
                            fileDeletedQueue.add(filePath)
                        }
                    }
                }
            }
            launch(Dispatchers.IO + job) {
                while (true) {
                    delay(200)
                    synchronized(fileCreatedQueue) {
                        if (fileCreatedQueue.isNotEmpty()) {
                            val filenames = fileCreatedQueue.toList()
                            fileCreatedQueue.clear()
                            println("dispatch: added $filenames")
                            repos.addFiles(ComicStorage[mainController.selectedComicId], filenames)
                        }
                    }
                    synchronized(fileDeletedQueue) {
                        if (fileDeletedQueue.isNotEmpty()) {
                            val filenames = fileDeletedQueue.toList()
                            fileDeletedQueue.clear()
                            println("dispatch: deleted $filenames")
                            repos.removeFiles(filenames)
                        }
                    }
                }
            }
        } catch (ex: UnsatisfiedLinkError) {
            // JNotify が正常にインストールされてない気がするけど
            // ファイル見張らないモードで一応起動する．
        }
    }

    override fun stop() {
        super.stop()
        job.cancel()
        Setting.save()
        repos.saveStructure()
    }
}