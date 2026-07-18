package to.sava.comicripper.controller

import javafx.fxml.FXML
import javafx.fxml.Initializable
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.TextField
import javafx.scene.layout.BorderPane
import javafx.scene.layout.GridPane
import javafx.scene.layout.Pane
import javafx.stage.Stage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import to.sava.comicripper.ext.FxDispatcher
import to.sava.comicripper.ext.loadFxml
import to.sava.comicripper.ext.setWindowIcon
import to.sava.comicripper.model.Setting
import java.net.URL
import java.util.*

class SettingController : BorderPane(), Initializable, CoroutineScope {
    private val job = Job()
    override val coroutineContext get() = FxDispatcher + job

    @FXML
    @Suppress("unused")
    private lateinit var settingScene: BorderPane

    @FXML
    private lateinit var settingGrid: GridPane

    @FXML
    private lateinit var close: Button

    private var stage: Stage? = null

    override fun initialize(location: URL?, resources: ResourceBundle?) {
        listOf(
            "作業ディレクトリ" to Setting.workDirectoryFlow,
            "格納ディレクトリ" to Setting.storeDirectoryFlow,
            "Tesseract 実行ファイル" to Setting.TesseractExeFlow
        ).forEachIndexed { row, (propText, flow) ->
            val label = Label().apply {
                styleClass.add("label")
                text = propText
            }
            val field = TextField().apply {
                styleClass.add("input")
                text = flow.value
                textProperty().addListener { _, _, newValue ->
                    flow.value = newValue
                }
            }
            settingGrid.add(label, 0, row)
            settingGrid.add(field, 1, row)
        }

        close.setOnAction {
            stage?.close()
        }
    }

    fun initStage(stage: Stage) {
        this.stage = stage
        stage.apply {
            setWindowIcon()
            width = Setting.settingWindowWidth
            height = Setting.settingWindowHeight
            if (Setting.settingWindowPosX >= 0.0) {
                x = Setting.settingWindowPosX
            }
            if (Setting.settingWindowPosY >= 0.0) {
                y = Setting.settingWindowPosY
            }
            widthProperty().addListener { _, _, v -> Setting.settingWindowWidth = v.toDouble() }
            heightProperty().addListener { _, _, v -> Setting.settingWindowHeight = v.toDouble() }
            xProperty().addListener { _, _, v -> Setting.settingWindowPosX = v.toDouble() }
            yProperty().addListener { _, _, v -> Setting.settingWindowPosY = v.toDouble() }

            setOnCloseRequest {
                job.cancel()
            }
        }
    }

    companion object {
        fun launchStage(owner: Stage) {
            val (settingPane, settingController) = loadFxml<Pane, SettingController>("setting.fxml")
            Stage().apply {
                initOwner(owner)
                settingController.initStage(this)
                scene = Scene(settingPane)
                show()
            }
        }
    }
}
