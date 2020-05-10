package to.sava.comicripper.ext

import javafx.fxml.FXMLLoader
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.Region

fun <P, C> Any.loadFxml(filename: String): Pair<P, C> {
    val loader = FXMLLoader(javaClass.getResource(filename))
    return Pair(loader.load(), loader.getController())
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
    imageProperty().set(image)
    val (width, height) = image.fitSize(fitX, fitY)
    fitWidth = width
    fitHeight = height
}