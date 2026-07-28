package to.sava.comicripper.domain.model

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.structuralEqualityPolicy
import io.github.oshai.kotlinlogging.KotlinLogging
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.util.*
import javax.imageio.ImageIO

private val logger = KotlinLogging.logger {}

/**
 * コミック1件。構成ファイルとそのサムネイルを保持する。
 *
 * 可変プロパティはすべて Compose の snapshot state で保持するため、変更は購読側へ自動的に伝わる
 * （[Stable] として扱えるので、Comic を引数に取る composable も引数比較でスキップできる）。
 *
 * 書き込みはファイル監視・画面操作と複数スレッドから起こる。snapshot state の更新自体は
 * ロックの下で行なわれるため同時変更でも壊れないが、まとめて1回の変更として見せたい範囲は
 * [addFiles]・[removeFiles] のようにスナップショットで囲う。
 *
 * 画像そのものは保持しない（[loadThumbnail]）。ページ数ぶんのサムネイルを抱えると
 * メモリを大きく食うため、表示に必要な形に加工したものを表示側が持つ。
 */
@Stable
class Comic(filename: String = "", val id: String = UUID.randomUUID().toString()) {
    companion object {
        const val COVER_ALBUM_PREFIX = "coverA"
        const val COVER_FULL_PREFIX = "coverF"
        const val COVER_STRIP_PREFIX = "coverS"
        const val PAGE_PREFIX = "page"
        val TARGET_REGEX =
            "^(?:${COVER_ALBUM_PREFIX}|${COVER_FULL_PREFIX}|${COVER_STRIP_PREFIX}|${PAGE_PREFIX}).*\\.jpg$".toRegex()

        /** `prefix_123.jpg` から並び替え用のキーを組み立てるための正規表現。 */
        private val NUMBERED_FILENAME_REGEX = """^(\w+)_(\d+)\.""".toRegex()

        /** 一覧のサムネイルは高さ128dpまでで表示するため、DPIスケール2倍までを見込んだ上限とする。 */
        private const val THUMBNAIL_MAX_PX = 256
        private const val FULL_SIZE_IMAGE_CACHE_CAPACITY = 10

        private val defaultThumbnailLoader: (String) -> BufferedImage? = { filename ->
            readImageOrNull(filename)?.let { scaleToFit(it, THUMBNAIL_MAX_PX, THUMBNAIL_MAX_PX) }
        }
        private val defaultFullSizeImageLoader: (String) -> BufferedImage? = { filename ->
            readImageOrNull(filename)
        }

        var thumbnailLoader = defaultThumbnailLoader
        var fullSizeImageLoader = defaultFullSizeImageLoader

        /** 実行時の作業ディレクトリを解決する。composition rootから起動時に一度配線される。 */
        var workDirectoryProvider: () -> String = { "" }

        fun resetImageLoaders() {
            thumbnailLoader = defaultThumbnailLoader
            fullSizeImageLoader = defaultFullSizeImageLoader
        }

        /**
         * `coverF_著者名｜題名.jpg` の形式のファイル名から著者名と題名を取り出す。
         * その形式でなければ、拡張子を除いたファイル名を著者名・題名の両方に使う。
         */
        private fun splitAuthorAndTitle(filename: String): Pair<String, String> {
            val base = filename.replace(".jpg", "")
            if (base.startsWith(COVER_FULL_PREFIX).not() || base.contains("｜").not()) {
                return base to base
            }
            val parts = base.removePrefix(COVER_FULL_PREFIX).removePrefix("_").split("｜")
            return parts[0] to parts[1]
        }

        private fun readImageOrNull(filename: String): BufferedImage? {
            return try {
                ImageIO.read(File("${workDirectoryProvider()}/$filename"))
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
    }

    var author by mutableStateOf(splitAuthorAndTitle(filename).first)

    var title by mutableStateOf(splitAuthorAndTitle(filename).second)

    private val _files = mutableStateListOf<String>()

    /**
     * ページ番号順に並べた構成ファイル。
     * 並べ替えの結果が変わったときだけ新しいリストになるので、そのまま remember のキーに使える。
     */
    val files: List<String> by derivedStateOf(structuralEqualityPolicy()) {
        _files.sortedBy { numberFormat(it) }
    }

    /**
     * 画像を読み直す必要が生じた回数。ディスク上のファイルが差し替わっても構成ファイル名は
     * 変わらないため、表示側がキャッシュを捨てる契機としてこれを見る。
     */
    var imageRevision by mutableStateOf(0)
        private set

    private fun numberFormat(filename: String): String {
        return NUMBERED_FILENAME_REGEX.find(filename)?.let {
            val (prefix, number) = it.destructured
            "${prefix}_%06d".format(number.toInt())
        } ?: filename
    }

    // 走査対象には並べ替え済みの [files] を使う。可変リストを直接辿ると、
    // 別スレッドからの追加・削除と重なったときに反復が壊れる。
    val coverAlbum: String?
        get() = files.firstOrNull { it.startsWith(COVER_ALBUM_PREFIX) }

    val coverFull: String?
        get() = files.firstOrNull { it.startsWith(COVER_FULL_PREFIX) }

    val coverStrip: String?
        get() = files.firstOrNull { it.startsWith(COVER_STRIP_PREFIX) }

    val coverFullImage: BufferedImage?
        get() = coverFull?.let { getFullSizeImage(it) }

    val isCoverFullLandscape: Boolean
        get() = coverFull?.let { filename ->
            getFullSizeImage(filename).let { it.width > it.height }
        } ?: false

    /** 最終アクセス順で管理するフルサイズ画像キャッシュ。上限を超えると最も長く使われていないものを追い出す。 */
    private val imageCache = Collections.synchronizedMap(
        object : LinkedHashMap<String, BufferedImage>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BufferedImage>) =
                size > FULL_SIZE_IMAGE_CACHE_CAPACITY
        }
    )

    init {
        addFile(filename)
    }

    fun getFullSizeImage(filename: String): BufferedImage {
        return loadFullSizeImage(filename)
    }

    /**
     * 複数ファイルをまとめて追加し、置き換えで外されたファイル名を返す。
     * 1ファイルずつの追加を購読側へ見せると、そのたびにサムネイルの作り直しを誘発するため、
     * 全ファイルの追加をスナップショットで囲んで1回の変更として適用する。
     */
    fun addFiles(filenames: List<String>): List<String> =
        Snapshot.withMutableSnapshot { filenames.mapNotNull { addFile(it) } }

    fun addFile(filename: String): String? {
        var replaced: String? = null
        // 存在判定は並べ替え済みの [files] ではなく実体を見る（要素数ぶんの並べ替えを誘発しないため）。
        if (filename.matches(TARGET_REGEX) && filename !in _files) {
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
            replaced?.let { removeFile(it) }
            _files.add(filename)
        }
        return replaced
    }

    /** 複数ファイルをまとめて削除する。[addFiles] と同じ理由でスナップショットで囲う。 */
    fun removeFiles(filenames: List<String>) {
        Snapshot.withMutableSnapshot { filenames.forEach { removeFile(it) } }
    }

    fun removeFile(filename: String) {
        if (filename in _files) {
            _files.remove(filename)
            imageCache.remove(filename)
        }
    }

    /**
     * 保持している画像を捨てて読み直させる。
     * ディスク上のファイルが外部から差し替えられたときに呼ぶ。
     */
    fun invalidateImages() {
        imageCache.clear()
        imageRevision += 1
    }

    /**
     * src の構成ファイルをすべて引き取る。
     * 引き取りと引き渡しの間の状態を購読側や保存処理へ見せないよう、1回の変更として適用する。
     */
    fun merge(src: Comic) {
        Snapshot.withMutableSnapshot {
            addFiles(src.files)
            src.removeFiles(src.files)
        }
    }

    fun mergeConflict(src: Comic): Boolean {
        return ((src.coverAlbum.isNullOrEmpty().not() && coverAlbum.isNullOrEmpty().not()) ||
                (src.coverFull.isNullOrEmpty().not() && coverFull.isNullOrEmpty().not()) ||
                (src.coverStrip.isNullOrEmpty().not() && coverStrip.isNullOrEmpty().not()))
    }

    /**
     * 一覧表示用に縮小した画像を読む。読んだ結果は保持しないので、必要な側がキャッシュすること
     * （全ページ分を抱えるとページ数に比例してメモリを食うため）。
     */
    fun loadThumbnail(filename: String): BufferedImage? = thumbnailLoader(filename)

    private fun loadFullSizeImage(filename: String): BufferedImage {
        imageCache[filename]?.let { return it }
        val image = checkNotNull(fullSizeImageLoader(filename)) { "no image for $filename" }
        imageCache[filename] = image
        return image
    }
}
