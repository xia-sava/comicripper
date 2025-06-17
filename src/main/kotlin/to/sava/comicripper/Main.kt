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
                            synchronized(fileCreatedQueue) {
                                fileCreatedQueue.add(filePath)
                            }
                        }
                    }
                    JNotify_win32.FILE_ACTION_REMOVED -> synchronized(fileDeletedQueue) {
                        if (filePath.matches(Comic.TARGET_REGEX)) {
                            synchronized(fileDeletedQueue) {
                                fileDeletedQueue.add(filePath)
                            }
                        }
                    }
                }
            }
            launch(Dispatchers.Default + job) {
                val createdFiles = mutableListOf<String>()
                val deletedFiles = mutableListOf<String>()
                while (true) {
                    synchronized(fileCreatedQueue) {
                        if (fileCreatedQueue.isNotEmpty()) {
                            createdFiles.addAll(fileCreatedQueue)
                            fileCreatedQueue.clear()
                        }
                    }
                    synchronized(fileDeletedQueue) {
                        if (fileDeletedQueue.isNotEmpty()) {
                            deletedFiles.addAll(fileDeletedQueue)
                            fileDeletedQueue.clear()
                        }
                    }
                    delay(200)
                    if (createdFiles.isNotEmpty()) {
                        repos.addFiles(createdFiles)
                        createdFiles.clear()
                    }
                    if (deletedFiles.isNotEmpty()) {
                        repos.removeFiles(deletedFiles)
                        deletedFiles.clear()
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

    companion object {
        const val VERSION = "0.6.4"
    }
}
