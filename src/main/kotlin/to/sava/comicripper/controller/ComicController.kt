package to.sava.comicripper.controller

import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.scene.canvas.Canvas
import javafx.scene.control.Label
import javafx.scene.control.Separator
import javafx.scene.image.ImageView
import javafx.scene.input.KeyCode
import javafx.scene.input.MouseButton
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.stage.Stage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import to.sava.comicripper.ext.FxDispatcher
import to.sava.comicripper.ext.fitImage
import to.sava.comicripper.ext.fitSize
import to.sava.comicripper.ext.toFxImage
import to.sava.comicripper.model.Comic
import to.sava.comicripper.ui.cutter.showCutterWindow
import to.sava.comicripper.ui.detail.showDetailWindow
import java.net.URL
import java.util.*

class ComicController : VBox(), Initializable, CoroutineScope {
    private val job = Job()
    override val coroutineContext get() = FxDispatcher + job

    @FXML
    private lateinit var comicScene: VBox

    @FXML
    private lateinit var author: Label

    @FXML
    private lateinit var title: Label

    @FXML
    private lateinit var imagesPane: HBox

    var comic: Comic? = null
        private set

    private var stage: Stage? = null

    private val clickListeners = mutableListOf<() -> Unit>()
    private val keyPressedListeners = mutableListOf<(KeyCode) -> Unit>()

    override fun initialize(location: URL?, resources: ResourceBundle?) {
        comicScene.setOnMouseClicked { event ->
            if (event.button == MouseButton.PRIMARY) {
                when (event.clickCount) {
                    1 -> invokeClickListener()
                    2 -> launchDetailWindow()
                }
            }
        }
        comicScene.setOnKeyPressed {
            when (it.code) {
                KeyCode.ENTER -> launchDetailWindow()
                else -> invokeKeyPressedListener(it.code)
            }
        }
    }

    fun destroy() {
        clickListeners.clear()
        job.cancel()
    }

    fun initStage(stage: Stage) {
        this.stage = stage
        stage.setOnCloseRequest {
            job.cancel()
        }
    }

    fun setComic(comic: Comic) {
        this.comic = comic
        updateComic()
        launch {
            comic.changeFlow.collect {
                updateComic()
            }
        }
    }

    private fun truncateForDisplay(text: String, maxLength: Int): String {
        if (text.length <= maxLength) return text

        val ellipsis = " … "
        val remainingLength = maxLength - ellipsis.length
        val frontLength = remainingLength / 2
        val backLength = remainingLength - frontLength

        return text.take(frontLength) + ellipsis + text.takeLast(backLength)
    }

    private fun updateComic() {
        val comic = this.comic ?: return

        val maxAuthorLength = 20
        val maxTitleLength = 40

        author.text = truncateForDisplay(comic.author, maxAuthorLength)
        title.text = truncateForDisplay(comic.title, maxTitleLength)

        imagesPane.children.clear()
        val thumbnails = comic.thumbnails.map { it.toFxImage() }
        if (thumbnails.isNotEmpty()) {
            imagesPane.children.add(ImageView().apply {
                fitImage(thumbnails.first(), 256.0, 128.0)
            })
            val images = thumbnails.drop(1)
            if (images.isNotEmpty()) {
                imagesPane.children.add(Separator().apply {
                    orientation = Orientation.VERTICAL
                    padding = Insets(4.0)
                })

                imagesPane.children.add(Canvas().apply {
                    val gc = graphicsContext2D
                    width = 128.0 + (images.size - 1) * 3.0
                    height = 128.0
                    images.reversed().forEachIndexed { i, image ->
                        val (w, h) = image.fitSize(128.0, 128.0)
                        val x = width - 128.0 - (3.0 * i)
                        gc.drawImage(image, x, 0.0, w, h)
                        gc.stroke = Color.SILVER
                        gc.strokeLine(x + w, 0.0, x + w, 128.0)
                    }
                })
            }
        }

        comicScene.layoutBoundsProperty().addListener { _, _, bounds ->
            comicScene.minWidth = bounds?.width ?: 0.0
        }
    }

    private fun launchDetailWindow() {
        comic?.let { comic ->
            if (
                comic.coverFull.isNullOrEmpty().not() &&
                comic.coverAlbum.isNullOrEmpty() &&
                comic.isCoverFullLandscape
            ) {
                showCutterWindow(comic)
            } else {
                showDetailWindow(comic)
            }
        }
    }

    fun addClickListener(listener: () -> Unit) {
        clickListeners.add(listener)
    }

    private fun invokeClickListener() {
        clickListeners.forEach {
            it()
        }
    }

    fun addKeyPressedListener(listener: (KeyCode) -> Unit) {
        keyPressedListeners.add(listener)
    }

    private fun invokeKeyPressedListener(code: KeyCode) {
        keyPressedListeners.forEach {
            it(code)
        }
    }
}
