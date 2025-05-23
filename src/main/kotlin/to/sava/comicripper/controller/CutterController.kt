package to.sava.comicripper.controller

import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.Group
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Slider
import javafx.scene.image.ImageView
import javafx.scene.input.KeyCode
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Region
import javafx.stage.Stage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import to.sava.comicripper.Main
import to.sava.comicripper.ext.loadFxml
import to.sava.comicripper.ext.setWindowIcon
import to.sava.comicripper.model.Comic
import to.sava.comicripper.model.Setting
import to.sava.comicripper.repository.ComicRepository
import tornadofx.*
import java.net.URL
import java.util.*
import kotlin.math.max
import kotlin.math.min

private const val WINDOW_TITLE = "comicripper ${Main.VERSION}"

class CutterController : BorderPane(), Initializable, CoroutineScope {
    private val job = Job()
    override val coroutineContext get() = Dispatchers.Main + job

    private val repos = ComicRepository()

    @FXML
    private lateinit var cutterScene: BorderPane

    @FXML
    private lateinit var imageView: ImageView

    @FXML
    private lateinit var leftLimit: Slider

    @FXML
    private lateinit var leftLine: Group

    @FXML
    private lateinit var rightLimit: Slider

    @FXML
    private lateinit var rightLine: Group

    @FXML
    private lateinit var doCutting: Button

    @FXML
    private lateinit var cancel: Button

    @FXML
    private lateinit var toDetailBox: HBox

    @FXML
    private lateinit var toDetail: Button

    private var comic: Comic? = null

    private var stage: Stage? = null

    override fun initialize(location: URL?, resources: ResourceBundle?) {
        cutterScene.setOnKeyPressed {
            val unit = 0.1
            when (it.code) {
                KeyCode.ESCAPE -> {
                    stage?.close()
                }
                KeyCode.LEFT -> {
                    when (it.isShiftDown) {
                        true -> rightLimit.value = max(rightLimit.value - unit, rightLimit.min)
                        false -> leftLimit.value = max(leftLimit.value - unit, leftLimit.min)
                    }
                }
                KeyCode.RIGHT -> {
                    when (it.isShiftDown) {
                        true -> rightLimit.value = min(rightLimit.value + unit, rightLimit.max)
                        false -> leftLimit.value = min(leftLimit.value + unit, leftLimit.max)
                    }
                }
                KeyCode.ENTER -> {
                    cut()
                }
                else -> return@setOnKeyPressed
            }
            it.consume()
        }

        leftLimit.value = Setting.cutterLeftPercent
        Setting.cutterLeftPercentProperty.bind(leftLimit.valueProperty())
        leftLimit.valueProperty().onChange {
            rescaleLimiter()
        }
        leftLimit.focusedProperty().onChange {
            imageView.requestFocus()
        }

        rightLimit.value = Setting.cutterRightPercent
        Setting.cutterRightPercentProperty.bind(rightLimit.valueProperty())
        rightLimit.valueProperty().onChange {
            rescaleLimiter()
        }
        rightLimit.focusedProperty().onChange {
            imageView.requestFocus()
        }

        imageView.apply {
            fitWidthProperty().bind(cutterScene.widthProperty())
            fitHeightProperty().bind(cutterScene.heightProperty() - 80.0)
            isPreserveRatio = true
        }

        doCutting.setOnAction {
            cut()
        }
        doCutting.requestFocus()

        cancel.setOnAction {
            stage?.close()
        }

        toDetail.setOnAction {
            launchDetail()
        }
    }

    fun initStage(stage: Stage) {
        this.stage = stage
        stage.apply {
            setWindowIcon()
            width = Setting.cutterWindowWidth
            height = Setting.cutterWindowHeight
            if (Setting.cutterWindowPosX >= 0.0) {
                x = Setting.cutterWindowPosX
            }
            if (Setting.cutterWindowPosY >= 0.0) {
                y = Setting.cutterWindowPosY
            }
            Setting.cutterWindowWidthProperty.bind(widthProperty())
            Setting.cutterWindowHeightProperty.bind(heightProperty())
            Setting.cutterWindowPosXProperty.bind(xProperty())
            Setting.cutterWindowPosYProperty.bind(yProperty())

            setOnCloseRequest {
                job.cancel()
            }
        }
    }

    fun setComic(comic: Comic) {
        this.comic = comic
        this.stage?.title = "${comic.title} ${comic.author} - $WINDOW_TITLE"

        comic.coverFullImage?.let {
            imageView.image = it
        }

        comic.coverAlbum?.let {
            toDetailBox.hide()
        }

        resizeScreen()
    }

    private fun cut() {
        comic?.let { comic ->
            launch {
                repos.cutCover(comic, leftLimit.value, rightLimit.value, rightLine.layoutBounds.width)
            }
        }
        launchDetail()
    }

    private fun resizeScreen() {
        if (comic == null) {
            return
        }
        ((leftLine.children[0] as HBox).children.first { it is Region } as Region).minHeight =
            cutterScene.height - 100.0
        ((rightLine.children[0] as HBox).children.first { it is Region } as Region).minHeight =
            cutterScene.height - 100.0
        rescaleLimiter()
    }

    private fun rescaleLimiter() {
        leftLine.translateX = (leftLimit.value - 50.0) * ((imageView.fitWidth - leftLine.layoutBounds.width) / 100)
        rightLine.translateX = (rightLimit.value - 50.0) * ((imageView.fitWidth - rightLine.layoutBounds.width) / 100)
    }

    private fun launchDetail() {
        comic?.let { comic ->
            stage?.let { stage ->
                stage.close()
                DetailController.launchStage(stage, comic)
            }
        }
    }

    companion object {
        fun launchStage(owner: Stage, comic: Comic) {
            val (cutterPane, cutterController) = loadFxml<BorderPane, CutterController>("cutter.fxml")
            Stage().apply {
                initOwner(owner)
                cutterController.initStage(this)
                scene = Scene(cutterPane)
                show()
            }
            cutterController.setComic(comic)
        }
    }
}
