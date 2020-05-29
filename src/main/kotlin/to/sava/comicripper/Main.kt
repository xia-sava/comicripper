package to.sava.comicripper

import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.layout.BorderPane
import javafx.stage.Stage
import kotlinx.coroutines.*
import net.contentobjects.jnotify.JNotify
import net.contentobjects.jnotify.JNotifyListener
import to.sava.comicripper.controller.MainController
import to.sava.comicripper.ext.loadFxml
import to.sava.comicripper.model.Comic
import to.sava.comicripper.model.Setting
import to.sava.comicripper.repository.ComicRepository

class Main : Application(), CoroutineScope {
    private val job = Job()
    override val coroutineContext get() = Dispatchers.Main + job

    private val repos = ComicRepository()

    private var jNotifyWatcher: Int = -1

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
            jNotifyWatcher = JNotify.addWatch(
                Setting.workDirectory,
                JNotify.FILE_CREATED or JNotify.FILE_DELETED,
                false,
                object : JNotifyListener {
                    override fun fileModified(wd: Int, rootPath: String?, name: String?) {}
                    override fun fileRenamed(wd: Int, rootPath: String?, oldName: String?, newName: String?) {}
                    override fun fileDeleted(wd: Int, rootPath: String?, name: String?) {
                        name?.let {
                            repos.removeFile(name)
                        }
                    }

                    override fun fileCreated(wd: Int, rootPath: String?, name: String?) {
                        name?.takeIf { Comic.TARGET_REGEX.matches(it) }?.let {
                            repos.addFile(name, mainController.selectedComicId)
                        }
                    }
                }
            )
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