package to.sava.comicripper.controller

import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.image.ImageView
import javafx.scene.layout.BorderPane
import javafx.stage.Stage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import to.sava.comicripper.ext.loadFxml
import to.sava.comicripper.ext.modalProgressDialog
import to.sava.comicripper.model.Comic
import to.sava.comicripper.model.Setting
import to.sava.comicripper.repository.ComicRepository
import to.sava.comicripper.repository.ComicStorage
import tornadofx.*
import java.net.URL
import java.util.*

class DetailController : BorderPane(), Initializable, CoroutineScope {
    private val job = Job()
    override val coroutineContext get() = Dispatchers.Main + job

    private val repos = ComicRepository()

    @FXML
    private lateinit var detailScene: BorderPane

    @FXML
    private lateinit var author: TextField

    @FXML
    private lateinit var title: TextField

    @FXML
    private lateinit var notifyLabel: Label

    @FXML
    private lateinit var isbn: TextField

    @FXML
    private lateinit var searchIsbn: Button

    @FXML
    private lateinit var ocrIsbn: Button

    @FXML
    private lateinit var cutter: Button

    @FXML
    private lateinit var zip: Button

    @FXML
    private lateinit var imageView: ImageView

    @FXML
    private lateinit var bottomBar: ToolBar

    @FXML
    private lateinit var leftButton: Button

    @FXML
    private lateinit var slider: Slider

    @FXML
    private lateinit var currentNumber: Label

    @FXML
    private lateinit var pageNumber: Label

    @FXML
    private lateinit var rightButton: Button

    private var comic: Comic? = null

    private var stage: Stage? = null

    override fun initialize(location: URL?, resources: ResourceBundle?) {
        author.textProperty().onChange {
            ComicStorage[comic?.id]?.let { comic -> comic.author = it ?: "" }
        }
        author.focusedProperty().onChange { focused ->
            if (focused) {
                author.selectAll()
            }
        }

        title.textProperty().onChange {
            ComicStorage[comic?.id]?.let { comic -> comic.title = it ?: "" }
        }
        title.focusedProperty().onChange { focused ->
            if (focused) {
                title.selectAll()
            }
        }

        searchIsbn.setOnAction {
            val (author, title) = repos.searchISBN(isbn.text)
            this.author.text = author
            this.title.text = title
        }

        ocrIsbn.setOnAction {
            comic?.let { comic ->
                val modal = modalProgressDialog("OCRしています", "画像から ISBN を読み取って著者名/作品名をサーチしてます", requireNotNull(stage))
                val job = this.coroutineContext + Job()
                modal.setOnCloseRequest {
                    job.cancel()
                }
                modal.show()
                launch(Dispatchers.IO + job) {
                    val (author_, title_) = repos.ocrISBN(comic)
                    withContext(Dispatchers.Main + job) {
                        author.text = author_
                        title.text = title_
                        modal.close()
                    }
                }
            }
        }

        cutter.setOnAction {
            launchCutter()
        }

        zip.setOnAction {
            comic?.let {
                launch {
                    repos.zipComic(it)
                    notifyLabel.text = "zipを作成しました"
                    launch {
                        delay(5000)
                        notifyLabel.text = ""
                    }
                }
            }
        }

        imageView.apply {
            fitWidthProperty().bind(detailScene.widthProperty())
            fitHeightProperty().bind(detailScene.heightProperty() - 80.0)
            isPreserveRatio = true
        }


        bottomBar.prefWidthProperty().bind(detailScene.widthProperty())

        slider.valueProperty().onChange {
            setImage(it.toInt())
            currentNumber.text = it.toInt().toString()
        }

        leftButton.setOnAction {
            if (slider.value > slider.min) {
                slider.value -= 1
            }
        }
        rightButton.setOnAction {
            if (slider.value < slider.max) {
                slider.value += 1
            }
        }
    }

    fun initStage(stage: Stage) {
        this.stage = stage
        stage.apply {
            width = Setting.detailWindowWidth
            height = Setting.detailWindowHeight
            Setting.detailWindowWidthProperty.bind(widthProperty())
            Setting.detailWindowHeightProperty.bind(heightProperty())
            setOnCloseRequest {
                job.cancel()
            }
        }
    }

    fun setComic(comic: Comic) {
        this.comic = comic
        this.stage?.title = "${comic.title} ${comic.author} - comicripper 0.0.1"
        updateComic()
        comic.addListener {
            updateComic()
        }
    }

    private fun updateComic() {
        val comic = this.comic ?: return

        author.text = comic.author
        title.text = comic.title

        val imageNum = comic.files.size
        pageNumber.text = imageNum.toString()
        slider.max = imageNum.toDouble()
        if (imageNum > 0) {
            setImage(if (slider.value.toInt() > imageNum) imageNum - 1 else slider.value.toInt())
        }
    }

    private fun setImage(num: Int) {
        comic?.images?.getOrNull(num)?.let { image ->
            imageView.image = image
        }
    }

    private fun launchCutter() {
        comic?.let { comic ->
            val (cutterPane, cutterController) = loadFxml<BorderPane, CutterController>("cutter.fxml")
            Stage().apply {
                this@DetailController.stage?.let { initOwner(it) }
                cutterController.initStage(this)
                scene = Scene(cutterPane)
                show()
            }
            cutterController.setComic(comic)
        }

    }
}