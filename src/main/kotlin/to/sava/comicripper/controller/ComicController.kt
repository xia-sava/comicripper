package to.sava.comicripper.controller

import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.control.Alert
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
    var comic: Comic? = null

    @FXML
    private lateinit var pane: VBox

    @FXML
    private lateinit var label: Label

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

    override fun initialize(location: URL?, resources: ResourceBundle?) {
        this.onLeftClick { alert(Alert.AlertType.CONFIRMATION, "click", "${comic?.coverAll}") }
    }

    fun updateComic(comic: Comic) {
        this.comic = comic
        label.text = comic.coverAll
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
}