package to.sava.comicripper.infrastructure.image

import io.github.oshai.kotlinlogging.KotlinLogging
import to.sava.comicripper.model.Setting
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.util.Collections
import javax.imageio.ImageIO

private val logger = KotlinLogging.logger {}

/** 一覧のサムネイルは高さ128dpまでで表示するため、DPIスケール2倍までを見込んだ上限とする。 */
private const val THUMBNAIL_MAX_PX = 256

/** 原寸画像をアプリ全体で何枚まで保持するか。同時に見る画面は実質1つなので少なくてよい。 */
private const val FULL_SIZE_CACHE_CAPACITY = 10

/**
 * 作業ディレクトリの画像を読む。
 *
 * 縮小した画像は保持しない。ページ数に比例してメモリを食うため、表示に必要な形へ加工したものを
 * 表示側が持つ。原寸画像はアプリ全体でひとつの最終アクセス順LRUに載せ、見終わったぶんが
 * 自然に追い出されるようにする。
 *
 * @param readImage ファイルから画像を読む処理。テストから差し替える。
 */
class ComicImageStore(
    private val setting: Setting,
    private val readImage: (File) -> BufferedImage? = { ImageIO.read(it) },
) {
    private val cache = Collections.synchronizedMap(
        object : LinkedHashMap<String, BufferedImage>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BufferedImage>) =
                size > FULL_SIZE_CACHE_CAPACITY
        }
    )

    /** 一覧表示用に縮小した画像を読む。結果は保持しないので、必要な側が加工した形で持つこと。 */
    fun loadThumbnail(filename: String): BufferedImage? =
        readOrNull(filename)?.let { scaleToFit(it, THUMBNAIL_MAX_PX, THUMBNAIL_MAX_PX) }

    /** 原寸の画像を読む。読めなければ例外を投げる。 */
    fun getFullSizeImage(filename: String): BufferedImage {
        cache[filename]?.let { return it }
        val image = checkNotNull(readOrNull(filename)) { "no image for $filename" }
        cache[filename] = image
        return image
    }

    /** 画像が横長かどうか。表紙が見開きのまま切り出されていないかの判定に使う。 */
    fun isLandscape(filename: String): Boolean =
        getFullSizeImage(filename).let { it.width > it.height }

    /** ディスク上で差し替えられたファイルの保持を捨てて、次の要求で読み直させる。 */
    fun invalidate(filenames: Collection<String>) {
        filenames.forEach { cache.remove(it) }
    }

    private fun readOrNull(filename: String): BufferedImage? =
        try {
            readImage(File("${setting.workDirectory}/$filename"))
        } catch (e: IOException) {
            logger.warn(e) { "image load failed: $filename" }
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
