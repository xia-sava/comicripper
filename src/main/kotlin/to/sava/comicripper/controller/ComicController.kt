package to.sava.comicripper.controller

import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.fxml.Initializable
import javafx.scene.control.Alert
import javafx.scene.control.Label
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import to.sava.comicripper.model.Comic
import tornadofx.add
import tornadofx.alert
import tornadofx.onLeftClick
import java.net.URL
import java.util.*

class ComicController(private val comic: Comic): VBox(), Initializable {
    @FXML
    private lateinit var label: Label

    @FXML
    private lateinit var imagesPane: HBox

    init {
        val loader = FXMLLoader(javaClass.getResource("../comic.fxml"))
        loader.setRoot(this)
        loader.setController(this)
        loader.load<VBox>()
    }

    override fun initialize(location: URL?, resources: ResourceBundle?) {
        label.text = comic.coverAll
        val image = Image(comic.coverAll, false)
        val imageView = ImageView(image).apply {
            minHeight = 128.0
            minWidth = image.width * (128 / image.height)
            fitHeight = minHeight
            fitWidth = minWidth
        }
        imagesPane.add(imageView)
        this.prefHeight += 24.0
        this.onLeftClick { alert(Alert.AlertType.CONFIRMATION, "click", "${this.comic.coverAll}") }
    }
}