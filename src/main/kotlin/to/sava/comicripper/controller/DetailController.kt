package to.sava.comicripper.controller

import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.image.ImageView
import javafx.scene.input.KeyCode
import javafx.scene.input.MouseButton
import javafx.scene.layout.BorderPane
import javafx.stage.Stage
import javafx.util.StringConverter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import to.sava.comicripper.ext.loadFxml
import to.sava.comicripper.ext.modalProgressDialog
import to.sava.comicripper.ext.setWindowIcon
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
    private lateinit var releaseImage: Button

    @FXML
    private lateinit var reloadImages: Button

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
    private lateinit var close: Button

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
        detailScene.setOnKeyPressed {
            when (it.code) {
                KeyCode.ESCAPE -> stage?.close()
                else -> {
                }
            }
        }
        listOf(author, title, isbn).forEach { textfield ->
            textfield.setOnKeyReleased {
                when (it.code) {
                    KeyCode.LEFT, KeyCode.RIGHT -> it.consume()
                    else -> {
                    }
                }
            }
        }

        author.textProperty().onChange {
            ComicStorage[comic?.id]?.let { comic -> comic.author = it ?: "" }
        }

        title.textProperty().onChange {
            ComicStorage[comic?.id]?.let { comic -> comic.title = it ?: "" }
        }

        releaseImage.setOnAction {
            releaseImage()
        }

        reloadImages.setOnAction {
            launch {
                comic?.reloadImages()
            }
        }

        val searchIsbnAction = {
            modalProgressDialog(
                "ISBN検索",
                "ISBN から著者名/作品名をサーチしてます",
                stage
            ) { job ->
                val (author_, title_) = repos.searchISBN(isbn.text)
                withContext(Dispatchers.Main + job) {
                    author.text = author_
                    title.text = title_
                }
            }
        }
        isbn.setOnAction {
            searchIsbnAction()
        }
        isbn.setOnKeyPressed {
            if (it.code == KeyCode.ENTER) {
                searchIsbnAction()
                it.consume()
            }
        }
        searchIsbn.setOnAction {
            searchIsbnAction()
        }

        ocrIsbn.setOnAction {
            comic?.let { comic ->
                modalProgressDialog(
                    "OCRしています",
                    "画像から ISBN を読み取って著者名/作品名をサーチしてます",
                    stage
                ) { job ->
                    launch(Dispatchers.IO + job) {
                        repos.ocrISBN(comic)?.let {
                            withContext(Dispatchers.Main + job) {
                                comic.author = it.first
                                comic.title = it.second
                            }
                        }
                    }
                }
            }
        }

        cutter.setOnAction {
            launchCutter()
        }

        zip.setOnAction {
            comic?.let { comic ->
                modalProgressDialog(
                    "ZIPしています",
                    "コミックをZIP化しています",
                    stage
                ) { job ->
                    repos.zipComic(comic)
                    withContext(Dispatchers.Main + job) {
                        stage?.close()
                    }
                }
            }
        }

        close.setOnAction {
            stage?.close()
        }

        imageView.apply {
            fitWidthProperty().bind(detailScene.widthProperty())
            fitHeightProperty().bind(detailScene.heightProperty() - 80.0)
            isPreserveRatio = true
        }
        imageView.setOnMouseClicked { event ->
            if (event.button == MouseButton.PRIMARY) {
                imageView.requestFocus()
                when (event.clickCount) {
                    1 -> slider.requestFocus()
                    2 -> {
                        comic?.let { comic ->
                            if (comic.coverAll.isNullOrEmpty().not()
                                && comic.coverFront.isNullOrEmpty()
                            ) {
                                launchCutter()
                            }
                        }
                    }
                }
            }
        }

        bottomBar.prefWidthProperty().bind(detailScene.widthProperty())

        slider.valueProperty().onChange {
            setImage(it.toInt())
            currentNumber.text = it.toInt().toString()
        }
        slider.labelFormatter = object : StringConverter<Double>() {
            override fun fromString(value: String?) = (value?.toDouble() ?: 1.0) - 1.0
            override fun toString(value: Double?) = ((value?.toInt() ?: 0) + 1).toString()
        }
        slider.setOnKeyPressed {
            when (it.code) {
                KeyCode.LEFT, KeyCode.RIGHT -> it.consume()
                else -> {
                }
            }
        }

        leftButton.setOnAction {
            leftImage()
        }
        rightButton.setOnAction {
            rightImage()
        }
    }

    fun initStage(stage: Stage) {
        this.stage = stage
        stage.apply {
            setWindowIcon()
            width = Setting.detailWindowWidth
            height = Setting.detailWindowHeight
            if (Setting.detailWindowPosX >= 0.0) {
                x = Setting.detailWindowPosX
            }
            if (Setting.detailWindowPosY >= 0.0) {
                y = Setting.detailWindowPosY
            }
            Setting.detailWindowWidthProperty.bind(widthProperty())
            Setting.detailWindowHeightProperty.bind(heightProperty())
            Setting.detailWindowPosXProperty.bind(xProperty())
            Setting.detailWindowPosYProperty.bind(yProperty())

            setOnCloseRequest {
                job.cancel()
            }
            setOnShown {
                slider.requestFocus()
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
        if (comic.files.size > 1 && comic.files[1] == comic.coverAll) {
            slider.value = 1.0
        }
    }

    private fun updateComic() {
        val comic = this.comic ?: return

        launch {
            if (author.text != comic.author) {
                author.text = comic.author
            }
            if (title.text != comic.title) {
                title.text = comic.title
            }

            val imageNum = comic.files.size
            pageNumber.text = imageNum.toString()
            slider.max = imageNum.toDouble() - 1
            if (imageNum > 0) {
                setImage(if (slider.value.toInt() > imageNum) imageNum - 1 else slider.value.toInt())
            }
        }
    }

    private fun setImage(num: Int) {
        comic?.let { comic ->
            comic.files.getOrNull(num)?.let { filename ->
                comic.getFullSizeImage(filename)?.let { image ->
                    imageView.image = image
                }
            }
        }
    }

    private fun releaseImage() {
        comic?.let { comic ->
            comic.files.getOrNull(slider.value.toInt())?.let { filename ->
                repos.releaseFile(comic, filename)
            }
        }
    }

    private fun launchCutter() {
        comic?.let { comic ->
            stage?.let { stage ->
                CutterController.launchStage(stage, comic)
            }
        }
    }

    private fun leftImage() {
        if (slider.value > slider.min) {
            slider.value -= 1
        }
    }

    private fun rightImage() {
        if (slider.value < slider.max) {
            slider.value += 1
        }
    }

    companion object {
        fun launchStage(owner: Stage, comic: Comic) {
            val (detailPane, detailController) = loadFxml<BorderPane, DetailController>("detail.fxml")
            Stage().apply {
                initOwner(owner)
                detailController.initStage(this)
                scene = Scene(detailPane)
                show()
            }
            detailController.setComic(comic)
        }
    }
}
