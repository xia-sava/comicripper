package to.sava.comicripper.ext

import javafx.fxml.FXMLLoader
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.control.ProgressIndicator
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.VBox
import javafx.stage.Modality
import javafx.stage.Stage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import to.sava.comicripper.model.Setting
import tornadofx.add
import tornadofx.paddingAll

object Loader

/**
 * FXML ファイルをロード
 */
fun <P, C> loadFxml(filename: String): Pair<P, C> {
    val loader = FXMLLoader(Loader.javaClass.getResource("/to/sava/comicripper/$filename"))
    return Pair(loader.load(), loader.getController())
}

/**
 * アイコン PNG の Image を作成
 */
fun Stage.setWindowIcon() {
    icons.add(Image(Loader.javaClass.getResourceAsStream("/to/sava/comicripper/icon.png")))
}

/**
 * fitX / fitY に収まる最小サイズの width / height を算出
 */
fun Image.fitSize(fitX: Double, fitY: Double): Pair<Double, Double> {
    val imageAspect = (width / height)
    val ratio = when {
        fitX == 0.0 -> fitY / height
        fitY == 0.0 -> fitX / width
        imageAspect >= (fitX / fitY) -> fitX / width
        imageAspect < (fitX / fitY) -> fitY / height
        else -> 1.0
    }
    return Pair(width * ratio, height * ratio)
}

fun ImageView.fitImage(image: Image, fitX: Double, fitY: Double) {
    this.image = image
    val (width, height) = image.fitSize(fitX, fitY)
    fitWidth = width
    fitHeight = height
}

fun workFilename(filename: String): String {
    return "${Setting.workDirectory}/$filename"
}

fun CoroutineScope.modalProgressDialog(
    title: String,
    text: String,
    owner: Stage?,
    block: suspend (job: Job) -> Any?
) {
    checkNotNull(owner)
    val job = Job()
    val modal = Stage().apply {
        this.title = title
        initModality(Modality.WINDOW_MODAL)
        initOwner(owner)
        scene = Scene(VBox().apply {
            paddingAll = 8.0
            alignment = Pos.CENTER
            add(Label(text))
            add(ProgressIndicator().apply {
                setPrefSize(24.0, 24.0)
            })
        })
        setOnCloseRequest {
            job.cancel()
        }
    }
    modal.show()
    launch(Dispatchers.IO + job) {
        when (val result = block(job)) {
            is Job -> result.join()
            is Iterable<*> -> result.filterIsInstance<Job>().joinAll()
        }
        withContext(Dispatchers.Main + job) {
            modal.close()
        }
    }
}
