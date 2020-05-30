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
        val TARGET_REGEX =
            "^(?:${COVER_FRONT_PREFIX}|${COVER_ALL_PREFIX}|${COVER_BELT_PREFIX}|${PAGE_PREFIX}).*\\.jpg$".toRegex()
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

    private val _files = mutableListOf<String>()
    val files: List<String> get() = _files.sorted()

    private val _thumbnails = mutableMapOf<String, Image>()
    val thumbnails get() = _thumbnails.toSortedMap().values

    val coverFront: String?
        get() = _files.firstOrNull { it.startsWith(COVER_FRONT_PREFIX) }

    val coverAll: String?
        get() = _files.firstOrNull { it.startsWith(COVER_ALL_PREFIX) }

    val coverBelt: String?
        get() = _files.firstOrNull { it.startsWith(COVER_BELT_PREFIX) }

    val coverAllImage: Image?
        get() = coverAll?.let { getFullSizeImage(it) }

    private val listeners = mutableListOf<(Comic) -> Unit>()

    private val imageCache = mutableListOf<Triple<Int, String, Image>>()

    init {
        addFile(filename)
    }

    fun getFullSizeImage(filename: String): Image? {
        return loadFullSizeImage(filename)
    }

    private fun addFiles(filenames: List<String>): List<String> {
        return filenames
            .mapNotNull { addFile(it, prependListener = true) }
            .also {
                invokeListener()
            }
    }

    fun addFile(filename: String, prependListener: Boolean = false): String? {
        var replaced: String? = null
        if (filename.matches(TARGET_REGEX) && filename !in files) {
            when {
                filename.startsWith(COVER_FRONT_PREFIX) -> {
                    replaced = coverFront
                }
                filename.startsWith(COVER_ALL_PREFIX) -> {
                    replaced = coverAll
                }
                filename.startsWith(COVER_BELT_PREFIX) -> {
                    replaced = coverBelt
                }
            }
            _files.add(filename)
            _thumbnails[filename] = loadImage(filename)
            if (prependListener.not()) {
                invokeListener()
            }
        }
        return replaced
    }

    fun removeFiles(filenames: List<String>) {
        filenames.forEach { removeFile(it, prependListener = true) }
        invokeListener()
    }

    fun removeFile(filename: String, prependListener: Boolean = false) {
        if (File("${Setting.workDirectory}/$filename").exists().not() && filename in files) {
            _files.remove(filename)
            _thumbnails.remove(filename)
        }
        if (prependListener.not()) {
            invokeListener()
        }
    }

    suspend fun reloadImages() {
        _thumbnails.keys.subtract(files).forEach {
            _thumbnails.remove(it)
        }
        files.forEach {
            _thumbnails[it] = loadImage(it)
            yield()
        }
    }

    fun merge(src: Comic) {
        addFiles(src.files)
        src.removeFiles(src.files)
    }

    fun mergeConflict(src: Comic): Boolean {
        return ((src.coverFront.isNullOrEmpty().not() && coverFront.isNullOrEmpty().not()) ||
                (src.coverAll.isNullOrEmpty().not() && coverAll.isNullOrEmpty().not()) ||
                (src.coverBelt.isNullOrEmpty().not() && coverBelt.isNullOrEmpty().not()))
    }

    private fun loadImage(filename: String): Image {
        val url = File("${Setting.workDirectory}/$filename").toURI().toURL().toString()
        return Image(url, 512.0, 512.0, true, true)
    }

    private fun loadFullSizeImage(filename: String): Image {
        val url = File("${Setting.workDirectory}/$filename").toURI().toURL().toString()
        if (imageCache.count { it.second == url } > 0) {
            return imageCache.first { it.second == url }.third
        }
        val num = (imageCache.maxBy { it.first }?.first ?: 0) + 1
        val image = Image(url)
        imageCache.add(Triple(num, url, image))
        if (imageCache.count() > 10) {
            imageCache.minBy { it.first }?.let {
                imageCache.remove(it)
            }
        }
        return image
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
