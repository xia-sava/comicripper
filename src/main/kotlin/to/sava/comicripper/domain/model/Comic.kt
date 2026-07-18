package to.sava.comicripper.domain.model

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.yield
import to.sava.comicripper.model.Setting
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.imageio.ImageIO

class Comic(filename: String = "") {
    companion object {
        const val COVER_ALBUM_PREFIX = "coverA"
        const val COVER_FULL_PREFIX = "coverF"
        const val COVER_STRIP_PREFIX = "coverS"
        const val PAGE_PREFIX = "page"
        val TARGET_REGEX =
            "^(?:${COVER_ALBUM_PREFIX}|${COVER_FULL_PREFIX}|${COVER_STRIP_PREFIX}|${PAGE_PREFIX}).*\\.jpg$".toRegex()

        private const val THUMBNAIL_MAX_PX = 512

        private val defaultThumbnailLoader: (String) -> BufferedImage? = { filename ->
            readImageOrNull(filename)?.let { scaleToFit(it, THUMBNAIL_MAX_PX, THUMBNAIL_MAX_PX) }
        }
        private val defaultFullSizeImageLoader: (String) -> BufferedImage? = { filename ->
            readImageOrNull(filename)
        }

        var thumbnailLoader = defaultThumbnailLoader
        var fullSizeImageLoader = defaultFullSizeImageLoader

        fun resetImageLoaders() {
            thumbnailLoader = defaultThumbnailLoader
            fullSizeImageLoader = defaultFullSizeImageLoader
        }

        private fun readImageOrNull(filename: String): BufferedImage? {
            return try {
                ImageIO.read(File("${Setting.workDirectory}/$filename"))
            } catch (e: IOException) {
                println("image load failed: $filename: ${e.message}")
                null
            }
        }

        /**
         * maxWidth x maxHeight に収まるサイズへアスペクト比を保って縮小する．
         * 元がそれ以下のサイズならそのまま返す．
         */
        private fun scaleToFit(source: BufferedImage, maxWidth: Int, maxHeight: Int): BufferedImage {
            val ratio = minOf(
                maxWidth.toDouble() / source.width,
                maxHeight.toDouble() / source.height,
            )
            if (ratio >= 1.0) {
                return source
            }
            val width = maxOf(1, (source.width * ratio).toInt())
            val height = maxOf(1, (source.height * ratio).toInt())
            return BufferedImage(width, height, BufferedImage.TYPE_INT_RGB).also { scaled ->
                scaled.createGraphics().apply {
                    setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR,
                    )
                    drawImage(source, 0, 0, width, height, null)
                    dispose()
                }
            }
        }
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

    private val _files = CopyOnWriteArrayList<String>()
    val files: List<String> get() = _files.sortedBy { numberFormat(it) }

    private val _thumbnails = ConcurrentHashMap<String, BufferedImage>()
    val thumbnails: List<BufferedImage>
        get() = _thumbnails.entries
            .sortedBy { numberFormat(it.key) }
            .map { it.value }

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

    val coverFullImage: BufferedImage?
        get() = coverFull?.let { getFullSizeImage(it) }

    val isCoverFullLandscape: Boolean
        get() = coverFull?.let { filename ->
            getFullSizeImage(filename).let { it.width > it.height }
        } ?: false

    private val _changeFlow = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val changeFlow: SharedFlow<Unit> = _changeFlow

    private val imageCache = CopyOnWriteArrayList<Triple<Int, String, BufferedImage>>()

    init {
        addFile(filename)
    }

    fun getFullSizeImage(filename: String): BufferedImage {
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
            loadImage(filename)?.let { _thumbnails[filename] = it }
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
        (_thumbnails.keys - _files.toSet()).forEach { _thumbnails.remove(it) }
        _files.forEach { filename ->
            loadImage(filename)?.let { _thumbnails[filename] = it }
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

    private fun loadImage(filename: String): BufferedImage? = thumbnailLoader(filename)

    private fun loadFullSizeImage(filename: String): BufferedImage {
        imageCache.firstOrNull { it.second == filename }?.let {
            return it.third
        }
        val num = (imageCache.maxByOrNull { it.first }?.first ?: 0) + 1
        val image = checkNotNull(fullSizeImageLoader(filename)) { "no image for $filename" }
        imageCache.add(Triple(num, filename, image))
        if (imageCache.size > 10) {
            imageCache.minByOrNull { it.first }?.let {
                imageCache.remove(it)
            }
        }
        return image
    }

    private fun invokeListener() {
        _changeFlow.tryEmit(Unit)
    }
}
