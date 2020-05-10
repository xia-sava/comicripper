package to.sava.comicripper.ext

import javafx.fxml.FXMLLoader

fun <P, C> Any.loadFxml(filename: String): Pair<P, C> {
    val loader = FXMLLoader(javaClass.getResource(filename))
    return Pair(loader.load(), loader.getController())
}
