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
            coverAllImage = if (value.isNotEmpty()) loadImage(value, fullSize=true) else null
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

    fun addFiles(filenames: List<String>): List<String> {
        return filenames.mapNotNull(::addFile)
    }

    fun addFile(filename: String): String? {
        var replaced: String? = null
        if (filename !in files) {
            when {
                filename.startsWith(COVER_FRONT_PREFIX) -> {
                    if (coverFront != "") {
                        replaced = coverFront
                    }
                    coverFront = filename
                }
                filename.startsWith(COVER_ALL_PREFIX) -> {
                    if (coverAll != "") {
                        replaced = coverAll
                    }
                    coverAll = filename
                }
                filename.startsWith(COVER_BELT_PREFIX) -> {
                    if (coverBelt != "") {
                        replaced = coverBelt
                    }
                    coverBelt = filename
                }
                filename.startsWith(PAGE_PREFIX) -> {
                    pagesList.add(filename)
                    pageImagesMap[filename] = loadImage(filename)
                    invokeListener()
                }
            }
        }
        return replaced
    }

    fun removeFiles(filenames: List<String>) {
        filenames.forEach(::removeFile)
    }

    fun removeFile(filename: String) {
        if (File("${Setting.workDirectory}/$filename").exists().not() && filename in files) {
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
        addFiles(src.files)
        src.removeFiles(src.files)
    }

    fun mergeConflict(src: Comic): Boolean {
        return ((src.coverFront != "" && coverFront != "") ||
                (src.coverAll != "" && coverAll != "") ||
                (src.coverBelt != "" && coverBelt != ""))
    }

    private fun loadImage(filename: String, fullSize: Boolean = false): Image {
        val url = File("${Setting.workDirectory}/$filename").toURI().toURL().toString()
        return if (fullSize)
            Image(url)
        else
            Image(url, 2048.0, 2048.0, true, true)
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
