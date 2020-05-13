package to.sava.comicripper.controller

import javafx.beans.property.SimpleObjectProperty
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.geometry.Orientation
import javafx.scene.Scene
import javafx.scene.canvas.Canvas
import javafx.scene.control.Label
import javafx.scene.control.Separator
import javafx.scene.image.ImageView
import javafx.scene.input.MouseButton
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.stage.Stage
import to.sava.comicripper.ext.fitImage
import to.sava.comicripper.ext.fitSize
import to.sava.comicripper.ext.loadFxml
import to.sava.comicripper.model.Comic
import tornadofx.*
import java.net.URL
import java.util.*

class ComicController : VBox(), Initializable {
    @FXML
    private lateinit var comicScene: VBox

    @FXML
    private lateinit var author: Label

    @FXML
    private lateinit var title: Label

    @FXML
    private lateinit var imagesPane: HBox

    val comicProperty = SimpleObjectProperty<Comic?>(null)
    private val comic get() = comicProperty.value

    private var stage: Stage? = null

    private val clickListeners = mutableListOf<() -> Unit>()

    override fun initialize(location: URL?, resources: ResourceBundle?) {
        comicScene.setOnMouseClicked { event ->
            if (event.button == MouseButton.PRIMARY) {
                when (event.clickCount) {
                    1 -> invokeClickListener()
                    2 -> launchDetailWindow()
                }
            }
        }
        comicProperty.onChange {
            it?.let { setComic(it) }
        }
    }

    fun destroy() {
        clickListeners.clear()
    }

    fun initStage(stage: Stage) {
        this.stage = stage
    }

    private fun setComic(comic: Comic) {
        updateComic()
        comic.addListener {
            updateComic()
        }
    }

    private fun updateComic() {
        val comic = this.comic ?: return
        author.text = comic.author
        title.text = comic.title

        imagesPane.clear()
        if (comic.images.isNotEmpty()) {
            imagesPane.add(ImageView().apply {
                fitImage(comic.images.first(), 256.0, 128.0)
            })
            val images = comic.images.drop(1)
            if (images.isNotEmpty()) {
                imagesPane.add(Separator().apply {
                    orientation = Orientation.VERTICAL
                    paddingAll = 4.0
                })

                imagesPane.add(Canvas().apply {
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

        comicScene.layoutBoundsProperty().onChange {
            comicScene.minWidth = it?.width ?: 0.0
        }
    }

    private fun launchDetailWindow() {
        comic?.let { comic ->
            val (detailPane, detailController) = loadFxml<BorderPane, DetailController>("detail.fxml")
            Stage().apply {
                detailController.initStage(this)
                this@ComicController.stage?.let { initOwner(it) }
                scene = Scene(detailPane)
                show()
            }
            detailController.setComic(comic)
        }
    }

    fun addClickListener(listener: () -> Unit) {
        clickListeners.add(listener)
    }

//    fun removeClickListener(listener: () -> Unit) {
//        clickListeners.remove(listener)
//    }

    private fun invokeClickListener() {
        clickListeners.forEach {
            it()
        }
    }
}