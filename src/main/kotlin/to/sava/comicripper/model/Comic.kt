package to.sava.comicripper.model

import javafx.scene.image.Image
import kotlinx.coroutines.yield
import java.io.File
import java.util.*

class Comic(filename: String = "") {
    companion object {
        const val COVER_FRONT_PREFIX = "coverA"
        const val COVER_ALL_PREFIX = "coverF"
        const val COVER_BELT_PREFIX = "coverS"
        const val PAGE_PREFIX = "page"
        val TARGET_REGEX = "^(?:${COVER_FRONT_PREFIX}|${COVER_ALL_PREFIX}|${COVER_BELT_PREFIX}|${PAGE_PREFIX}).*\\.jpg$".toRegex()
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
            coverFrontImage = if (value.isNotEmpty()) loadImage(value) else null
            invokeListener()
        }
    var coverFrontImage: Image? = null

    var coverAll: String = ""
        set(value) {
            field = value
            coverAllImage = if (value.isNotEmpty()) loadImage(value) else null
            invokeListener()
        }
    var coverAllImage: Image? = null

    var coverBelt: String = ""
        set(value) {
            field = value
            coverBeltImage = if (value.isNotEmpty()) loadImage(value) else null
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

    fun addFile(vararg filenames: String): List<String> {
        val replaced = mutableListOf<String>()
        filenames.forEach { filename ->
            if (filename !in files) {
                when {
                    filename.startsWith(COVER_FRONT_PREFIX) -> {
                        if (coverFront != "") {
                            replaced.add(coverFront)
                        }
                        coverFront = filename
                    }
                    filename.startsWith(COVER_ALL_PREFIX) -> {
                        if (coverAll != "") {
                            replaced.add(coverAll)
                        }
                        coverAll = filename
                    }
                    filename.startsWith(COVER_BELT_PREFIX) -> {
                        if (coverBelt != "") {
                            replaced.add(coverBelt)
                        }
                        coverBelt = filename
                    }
                    filename.startsWith(PAGE_PREFIX) -> {
                        addPage(filename)
                    }
                }
            }
        }
        return replaced
    }

    fun removeFiles(vararg filenames: String) {
        filenames.forEach { filename ->
            if (filename in files) {
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

    suspend fun reloadImages() {
        if (coverFront.isNotEmpty()) {
            coverFrontImage = loadImage(coverFront)
            yield()
        }
        if (coverAll.isNotEmpty()) {
            coverAllImage = loadImage(coverAll)
            yield()
        }
        if (coverBelt.isNotEmpty()) {
            coverBeltImage = loadImage(coverBelt)
            yield()
        }
        files.forEach { filename ->
            loadImage(filename)
            yield()
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

//    fun removeListener(listener: (Comic) -> Unit) {
//        listeners.remove(listener)
//    }

    private fun invokeListener() {
        listeners.forEach {
            it(this)
        }
    }
}
