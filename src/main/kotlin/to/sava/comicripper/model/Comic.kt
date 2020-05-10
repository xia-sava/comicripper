package to.sava.comicripper.model

import javafx.beans.property.SimpleListProperty
import javafx.collections.ListChangeListener
import javafx.scene.image.Image
import tornadofx.*

class Comic(filename: String) {
    var author = filename.replace(".jpg", "")
        set(value) {
            field = value
            invokeListener()
        }

    var title = filename.replace(".jpg", "")
        set(value) {
            field = value
            invokeListener()
        }

    var coverFront: String = ""
        set(value) {
            field = value
            coverFrontImage = loadImage(value)
            invokeListener()
        }
    var coverFrontImage: Image? = null

    var coverAll: String = ""
        set(value) {
            field = value
            coverAllImage = loadImage(value)
            invokeListener()
        }
    var coverAllImage: Image? = null

    var coverBelt: String = ""
        set(value) {
            field = value
            coverBeltImage = loadImage(value)
            invokeListener()
        }
    var coverBeltImage: Image? = null

    val pagesProperty = SimpleListProperty<String>(observableListOf())
    val pages get() = pagesProperty.value.toList()

    private val listeners = mutableListOf<(Comic) -> Unit>()

    init {
        pagesProperty.addListener { change: ListChangeListener.Change<out String> ->
            while (change.next()) {
                when {
                    change.wasAdded() || change.wasRemoved() -> invokeListener()
                }
            }
        }
    }

    private fun loadImage(url: String): Image {
        return Image(url, false)
    }

    fun addListener(listener: (Comic) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (Comic) -> Unit) {
        listeners.remove(listener)
    }

    private fun invokeListener() {
        listeners.forEach {
            it(this)
        }
    }
}
