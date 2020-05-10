package to.sava.comicripper.controller

import javafx.beans.property.SimpleObjectProperty
import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.Label
import javafx.scene.image.ImageView
import javafx.scene.input.MouseButton
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import to.sava.comicripper.ext.fitImage
import to.sava.comicripper.ext.fitSize
import to.sava.comicripper.model.Comic
import tornadofx.onChange
import java.net.URL
import java.util.*

class ComicController : VBox(), Initializable {
    @FXML
    private lateinit var pane: VBox

    @FXML
    private lateinit var author: Label

    @FXML
    private lateinit var title: Label

    @FXML
    private lateinit var imagesPane: HBox

    @FXML
    private lateinit var coverFront: ImageView

    @FXML
    private lateinit var coverAll: ImageView

    @FXML
    private lateinit var coverBelt: ImageView

    @FXML
    private lateinit var pages: ImageView

    val comicProperty = SimpleObjectProperty<Comic?>(null)
    private val comic get() = comicProperty.value

    private val clickListeners = mutableListOf<() -> Unit>()
    private val doubleClickListeners = mutableListOf<() -> Unit>()

    override fun initialize(location: URL?, resources: ResourceBundle?) {
        pane.setOnMouseClicked { event ->
            if (event.button == MouseButton.PRIMARY) {
                when (event.clickCount) {
                    1 -> invokeClickListener()
                    2 -> invokeDoubleClickListener()
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

        comic.coverFrontImage?.let {
            coverFront.fitImage(it, 128.0, 128.0)
        }
        comic.coverAllImage?.let {
            coverAll.fitImage(it, 320.0, 128.0)
        }
        comic.coverBeltImage?.let {
            coverBelt.fitImage(it, 128.0, 128.0)
        }

        pane.layoutBoundsProperty().onChange {
            pane.minWidth = it?.width ?: 0.0
        }
    }

    fun addClickListener(listener: () -> Unit) {
        clickListeners.add(listener)
    }

    fun removeClickListener(listener: () -> Unit) {
        clickListeners.remove(listener)
    }

    private fun invokeClickListener() {
        clickListeners.forEach {
            it()
        }
    }

    fun addDoubleClickListener(listener: () -> Unit) {
        doubleClickListeners.add(listener)
    }

    fun removeDoubleClickListener(listener: () -> Unit) {
        doubleClickListeners.remove(listener)
    }

    private fun invokeDoubleClickListener() {
        doubleClickListeners.forEach {
            it()
        }
    }
}