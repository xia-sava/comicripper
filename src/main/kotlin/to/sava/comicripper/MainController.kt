package to.sava.comicripper

import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.layout.*
import to.sava.comicripper.controller.ComicController
import to.sava.comicripper.model.Comic
import tornadofx.add
import tornadofx.clear
import java.net.URL
import java.util.*

class MainController: Initializable {
    @FXML
    private lateinit var comicsList: VBox

    @FXML
    private lateinit var pane: BorderPane

    override fun initialize(location: URL?, resources: ResourceBundle?) {}

    fun updateComics(comics: List<Comic>) {
        comicsList.clear()
        comics.map { ComicController(it) }.forEach { comicsList.add(it) }
    }
}