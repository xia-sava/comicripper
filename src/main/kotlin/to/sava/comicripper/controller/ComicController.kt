package to.sava.comicripper.controller

import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.Label
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import to.sava.comicripper.model.Comic
import tornadofx.*
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

    private var comic: Comic? = null

    private val clickListeners = mutableListOf<() -> Unit>()


    override fun initialize(location: URL?, resources: ResourceBundle?) {
        pane.onLeftClick {
            invokeClickListener()
        }
    }

    fun destroy() {
        clickListeners.clear()
    }

    fun setComic(comic: Comic) {
        this.comic = comic
        updateComic()
        comic.addListener {
            updateComic()
        }
    }

    private fun updateComic() {
        val comic = this.comic ?: return
        author.text = comic.author
        title.text = comic.title
        coverAll.apply {
            imageProperty().set(Image(comic.coverAll, false))
            prefHeight = 128.0
            prefWidth = image.width * (128 / image.height)
            fitHeight = prefHeight
            fitWidth = prefWidth
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
}