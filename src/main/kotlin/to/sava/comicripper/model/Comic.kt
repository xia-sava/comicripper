package to.sava.comicripper.model

import javafx.scene.image.Image
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

    private val pagesList = mutableListOf<String>()
    val pages get() = pagesList.toList().sorted()

    private val pageImagesMap = mutableMapOf<String, Image>()
    val pageImages get() = pageImagesMap.toSortedMap().values

    val files: List<String>
        get() = (listOf(coverFront, coverAll, coverBelt) + pages).filter { it != "" }

    val images: List<Image>
        get() = (listOf(coverFrontImage, coverAllImage, coverBeltImage) + pageImages).filterNotNull()

    private val listeners = mutableListOf<(Comic) -> Unit>()

    init {
        addFile(filename)
    }

    fun addPage(vararg filenames: String) {
        pagesList.addAll(filenames)
        pageImagesMap.putAll(filenames.map { it to loadImage(it) }.toMap())
        invokeListener()
    }

    fun addFile(vararg filenames: String) {
        filenames.forEach { filename ->
            if (filename !in files) {
                when {
                    filename.startsWith(COVER_FRONT_PREFIX) -> coverFront = filename
                    filename.startsWith(COVER_ALL_PREFIX) -> coverAll = filename
                    filename.startsWith(COVER_BELT_PREFIX) -> coverBelt = filename
                    filename.startsWith(PAGE_PREFIX) -> addPage(filename)
                }
            }
        }
    }

    fun rescanFiles() {
        files.forEach { filename ->
            if (!File("${Setting.workDirectory}/$filename").exists()) {
                when (filename) {
                    coverFront -> coverFront = ""
                    coverAll -> coverAll = ""
                    coverBelt -> coverBelt = ""
                    in pages -> {
                        pageImagesMap.remove(filename)
                        pagesList.remove(filename)
                        invokeListener()
                    }
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
            pagesList.addAll(src.pages)
            pageImagesMap.putAll(src.pages.zip(src.images))
            invokeListener()
        }
    }

    fun mergeConflict(src: Comic): Boolean {
        return ((src.coverFront != "" && coverFront != "") ||
                (src.coverAll != "" && coverAll != "") ||
                (src.coverBelt != "" && coverBelt != ""))
    }

    private fun loadImage(filename: String): Image {
        return Image(File("${Setting.workDirectory}/$filename").toURI().toURL().toString())
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
