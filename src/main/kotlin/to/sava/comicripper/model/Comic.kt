package to.sava.comicripper.model

import javafx.beans.property.SimpleListProperty
import javafx.collections.ListChangeListener
import javafx.scene.image.Image
import tornadofx.*
import java.io.File
import java.util.*

class Comic(filename: String = "") {
    companion object {
        const val COVER_FRONT_PREFIX = "coverA"
        const val COVER_ALL_PREFIX = "coverF"
        const val COVER_BELT_PREFIX = "coverS"
        const val PAGE_PREFIX = "page"
    }

    var id = UUID.randomUUID().toString()

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
    val pages get() = pagesProperty.value.toMutableList()

    val files: List<String>
        get() {
            return (listOf(coverFront, coverAll, coverBelt) + pages).filter { it != "" }
        }

    private val listeners = mutableListOf<(Comic) -> Unit>()

    init {
        when {
            filename.startsWith(COVER_FRONT_PREFIX) -> coverFront = filename
            filename.startsWith(COVER_ALL_PREFIX) -> coverAll = filename
            filename.startsWith(COVER_BELT_PREFIX) -> coverBelt = filename
            filename.startsWith(PAGE_PREFIX) -> pagesProperty.add(filename)
        }

        pagesProperty.addListener { change: ListChangeListener.Change<out String> ->
            while (change.next()) {
                when {
                    change.wasAdded() || change.wasRemoved() -> invokeListener()
                }
            }
        }
    }

    fun merge(src: Comic) {
        if (src.coverFront != "") {
            coverFront = src.coverFront
        }
        if (src.coverAll != "") {
            coverAll = src.coverAll
        }
        if (src.coverBelt != "") {
            coverBelt = src.coverBelt
        }
        if (src.pages.isNotEmpty()) {
            pages.addAll(src.pages)
        }
    }

    private fun loadImage(filename: String): Image {
        return Image(File("${Setting.workDirectory}/$filename").toURI().toURL().toString(), false)
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
