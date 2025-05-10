package to.sava.comicripper.model

import javafx.scene.image.Image
import kotlinx.coroutines.yield
import java.io.File
import java.util.*

class Comic(filename: String = "") {
    companion object {
        const val COVER_ALBUM_PREFIX = "coverA"
        const val COVER_FULL_PREFIX = "coverF"
        const val COVER_STRIP_PREFIX = "coverS"
        const val PAGE_PREFIX = "page"
        val TARGET_REGEX =
            "^(?:${COVER_ALBUM_PREFIX}|${COVER_FULL_PREFIX}|${COVER_STRIP_PREFIX}|${PAGE_PREFIX}).*\\.jpg$".toRegex()
    }

    var id = UUID.randomUUID().toString()

    var author = filename.replace(".jpg", "").let {
        if (it.startsWith(COVER_FULL_PREFIX) && it.contains("｜")) {
            it.removePrefix(COVER_FULL_PREFIX).removePrefix("_").split("｜")[0]
        } else {
            it
        }
    }
        set(value) {
            field = value
            invokeListener()
        }

    var title = filename.replace(".jpg", "").let {
        if (it.startsWith(COVER_FULL_PREFIX) && it.contains("｜")) {
            it.removePrefix(COVER_FULL_PREFIX).removePrefix("_").split("｜")[1]
        } else {
            it
        }
    }
        set(value) {
            field = value
            invokeListener()
        }

    private val _files = mutableListOf<String>()
    val files: List<String> get() = _files.sortedBy { numberFormat(it) }

    private val _thumbnails = mutableMapOf<String, Image>()
    val thumbnails
        get() = _thumbnails.toList()
            .sortedBy { numberFormat(it.first) }
            .map { it.second }

    private fun numberFormat(filename: String): String {
        return """^(\w+)_(\d+)\.""".toRegex().find(filename)?.let {
            val (prefix, number) = it.destructured
            "${prefix}_%06d".format(number.toInt())
        } ?: filename
    }

    val coverAlbum: String?
        get() = _files.firstOrNull { it.startsWith(COVER_ALBUM_PREFIX) }

    val coverFull: String?
        get() = _files.firstOrNull { it.startsWith(COVER_FULL_PREFIX) }

    val coverStrip: String?
        get() = _files.firstOrNull { it.startsWith(COVER_STRIP_PREFIX) }

    val coverFullImage: Image?
        get() = coverFull?.let { getFullSizeImage(it) }

    val isCoverFullLandscape: Boolean
        get() = coverFull?.let { filename ->
            getFullSizeImage(filename).let { it.width > it.height }
        } ?: false

    private val listeners = mutableListOf<(Comic) -> Unit>()

    private val imageCache = mutableListOf<Triple<Int, String, Image>>()

    init {
        addFile(filename)
    }

    fun getFullSizeImage(filename: String): Image {
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
                filename.startsWith(COVER_ALBUM_PREFIX) -> {
                    replaced = coverAlbum
                }

                filename.startsWith(COVER_FULL_PREFIX) -> {
                    replaced = coverFull
                }

                filename.startsWith(COVER_STRIP_PREFIX) -> {
                    replaced = coverStrip
                }
            }
            replaced?.let { removeFile(it, prependListener = false) }
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
        if (filename in files) {
            _files.remove(filename)
            _thumbnails.remove(filename)
            imageCache.firstOrNull { it.second == filename }?.let { imageCache.remove(it) }
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
        return ((src.coverAlbum.isNullOrEmpty().not() && coverAlbum.isNullOrEmpty().not()) ||
                (src.coverFull.isNullOrEmpty().not() && coverFull.isNullOrEmpty().not()) ||
                (src.coverStrip.isNullOrEmpty().not() && coverStrip.isNullOrEmpty().not()))
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
        val num = (imageCache.maxByOrNull { it.first }?.first ?: 0) + 1
        val image = Image(url)
        imageCache.add(Triple(num, url, image))
        if (imageCache.count() > 10) {
            imageCache.minByOrNull { it.first }?.let {
                imageCache.remove(it)
            }
        }
        return image
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
