package to.sava.comicripper.infrastructure.repository

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.structuralEqualityPolicy
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import to.sava.comicripper.domain.model.Comic
import to.sava.comicripper.ext.workFilename
import to.sava.comicripper.infrastructure.image.ComicImageStore
import to.sava.comicripper.infrastructure.service.BookInfoSearcher
import to.sava.comicripper.infrastructure.text.normalizeText
import to.sava.comicripper.model.Setting
import to.sava.comicripper.model.quarantineBrokenFile
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

private val logger = KotlinLogging.logger {}

/** ファイル名から連番部分を取り出すための正規表現。 */
private val FILENAME_NUMBER_REGEX = """\d+""".toRegex()

class ComicRepository(
    private val setting: Setting,
    private val comicStorage: ComicStorage,
    private val imageStore: ComicImageStore,
    private val bookInfoSearcher: BookInfoSearcher,
) {

    fun reScanFiles(targetComic: Comic? = null) {
        val dir = File(setting.workDirectory)
        val structuredFiles = comicStorage.files
        dir.listFiles { file -> file.name.matches(Comic.TARGET_REGEX) }?.map {
            if (it.name !in structuredFiles) {
                val comic = Comic(it.name)
                if (targetComic != null) {
                    if (targetComic.mergeConflict(comic)) {
                        comicStorage.add(comic)
                    } else {
                        targetComic.merge(comic)
                    }
                } else {
                    comicStorage.add(comic)
                }
            }
        }
        comicStorage.all.forEach { comic ->
            comic.removeFiles(comic.files.filter {
                File("${setting.workDirectory}/$it").exists().not()
            })
            if (comic.files.isEmpty()) {
                comicStorage.remove(comic)
            }
        }
    }

    fun addFiles(filenames: List<String>) {
        filenames.forEach { addFile(it) }
    }

    private fun addFile(filename: String) {
        // 既にどこかのコミックに属していれば何もしない。
        // 生成した側が先に登録済みの場合に、監視イベントで二重に取り込まないようにする。
        if (comicStorage.all.any { filename in it.files }) {
            return
        }
        if (filename.startsWith(Comic.COVER_FULL_PREFIX)) {
            Comic(filename).let {
                comicStorage.add(it)
                comicStorage.targetId = it.id
            }
        } else {
            val target = comicStorage.target
            if (target != null) {
                target.addFile(filename)?.let {
                    comicStorage.add(Comic(it))
                }
            } else {
                comicStorage.add(Comic(filename))
            }
        }
    }

    fun removeFiles(filenames: List<String>) {
        filenames.forEach(::removeFile)
    }

    private fun removeFile(filename: String) {
        comicStorage.all.forEach { it.removeFile(filename) }
        comicStorage.removeEmpty()
        // 同じ名前で別の画像が置かれても古いものを見せないよう、保持を捨てる。
        imageStore.invalidate(listOf(filename))
    }

    /** ディスク上で差し替えられた画像を読み直させる。 */
    fun reloadImages(comic: Comic) {
        imageStore.invalidate(comic.files)
        comic.markImagesChanged()
    }

    suspend fun cutCover(comic: Comic, leftPercent: Double, rightPercent: Double) {
        if (comic.coverAlbum.isNullOrEmpty().not()) {
            File("${setting.workDirectory}/${comic.coverAlbum}").delete()
        }

        withContext(Dispatchers.IO) {
            val coverFull = checkNotNull(comic.coverFull)
            val coverFullImage = imageStore.getFullSizeImage(coverFull)
            val imageWidth = coverFullImage.width.toDouble()
            val imageHeight = coverFullImage.height
            val leftX = imageWidth * (leftPercent / 100.0)
            val rightX = imageWidth * (rightPercent / 100.0)
            val croppedWidth = rightX - leftX

            val outputImage = BufferedImage(croppedWidth.toInt(), imageHeight, BufferedImage.TYPE_INT_RGB)
            outputImage.createGraphics().apply {
                color = Color.WHITE
                fillRect(0, 0, outputImage.width, outputImage.height)
                drawImage(coverFullImage, -leftX.toInt(), 0, null)
                dispose()
            }
            val outputFilename = generateFilename(Comic.COVER_ALBUM_PREFIX)
            ImageIO.write(outputImage, "jpeg", File("${setting.workDirectory}/$outputFilename"))
            // 監視イベント経由で取り込むと、その時点の選択によっては別のコミックへ入ってしまう。
            comic.addFile(outputFilename)
        }
    }

    /**
     * コミックをZIPへまとめ、元の画像ファイルを削除する。
     * 元を消す操作のため、書き込み途中で失敗しても成果物が壊れないよう、
     * 同一ディレクトリの一時ファイルへ書いてから rename で置き換える。
     * 著者名・題名は手入力もできるので、ファイル名に使えない文字を含みうる。
     */
    fun zipComic(comic: Comic) {
        val zipFile = File(
            "${setting.storeDirectory}/${normalizeText(comic.author)}/${normalizeText(comic.title)}.zip"
        )
        zipFile.parentFile.mkdirs()
        val tempFile = File.createTempFile("comicripper", ".tmp", zipFile.absoluteFile.parentFile)
        try {
            ZipOutputStream(BufferedOutputStream(tempFile.outputStream())).use { zipStream ->
                var pageNum = 1
                comic.files.forEach { src ->
                    val name = when {
                        src.startsWith(Comic.COVER_ALBUM_PREFIX) -> Comic.COVER_ALBUM_PREFIX
                        src.startsWith(Comic.COVER_FULL_PREFIX) -> Comic.COVER_FULL_PREFIX
                        src.startsWith(Comic.COVER_STRIP_PREFIX) -> Comic.COVER_STRIP_PREFIX
                        else -> "page_%03d".format(pageNum++)
                    } + ".jpg"
                    // JPEGは既に圧縮済みのため、DEFLATEでの再圧縮を避けてSTOREDで格納する。
                    val bytes = Files.readAllBytes(Paths.get("${setting.workDirectory}/$src"))
                    val entry = ZipEntry(name).apply {
                        method = ZipEntry.STORED
                        size = bytes.size.toLong()
                        crc = CRC32().apply { update(bytes) }.value
                    }
                    zipStream.putNextEntry(entry)
                    zipStream.write(bytes)
                }
            }
            Files.move(
                tempFile.toPath(),
                zipFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            tempFile.delete()
        }
        comicStorage.remove(comic)
        comic.files
            .map { Paths.get("${setting.workDirectory}/$it") }
            .forEach { Files.deleteIfExists(it) }
    }

    fun pagesToComic(comic: Comic) {
        comicStorage.all
            .filter { it.files.size == 1 && it.files.first().startsWith(Comic.PAGE_PREFIX) }
            .forEach {
                comic.merge(it)
                comicStorage.remove(it)
            }
    }

    fun releaseFile(comic: Comic, filename: String) {
        if (filename in comic.files) {
            comic.removeFile(filename)
            comicStorage.add(Comic(filename))
        }
    }

    suspend fun ocrISBN(comic: Comic): Pair<String, String>? {
        val coverFull = comic.coverFull ?: return null
        val tmp = withContext(Dispatchers.IO) {
            Files.createTempFile(Paths.get(setting.workDirectory), "_tmp", "")
        }
        // 配布形態によっては標準出力の行き先が無いため、tesseract の出力はファイルへ受けてログへ回す。
        val processLog = File("$tmp.log")
        val exitCode = withContext(Dispatchers.IO) {
            try {
                val process = ProcessBuilder(
                    setting.tesseractExe,
                    workFilename(coverFull, setting.workDirectory),
                    tmp.toString(),
                    "-l", "jpn",
                    "--psm", "11"
                )
                    .redirectErrorStream(true)
                    .redirectOutput(processLog)
                    .start()
                try {
                    // ブロッキングで待つと取り消しに応じられないため、中断点のある待ち方をする。
                    process.onExit().await().exitValue()
                } finally {
                    if (process.isAlive) {
                        process.destroyForcibly()
                    }
                }
            } catch (e: IOException) {
                logger.warn(e) { "tesseract start failed: ${setting.tesseractExe}" }
                null
            }
        }
        return try {
            when {
                exitCode == null -> Pair("エラー", "cant find Tesseract")
                exitCode != 0 -> {
                    val output = processLog.takeIf { it.exists() }?.readText()?.trim().orEmpty()
                    logger.warn { "tesseract exited with $exitCode: $output" }
                    Pair("エラー", "OCRに失敗しました")
                }
                else -> File("$tmp.txt").readText()
                    .replace(" ", "")
                    .replace("\n", " ")
                    .replace("-", "")
                    .let { """(978\d{10}|ISBN(?:\d\D*){13})""".toRegex().find(it) }
                    ?.groupValues?.get(1)
                    ?.replace("""\D""".toRegex(), "")
                    ?.replace("""^(\d{13}).*$""".toRegex(), "$1")
                    ?.let { isbn ->
                        bookInfoSearcher.search(isbn)
                    }
                    ?: Pair("エラー", "ISBN不明")
            }
        } finally {
            tmp.toFile().delete()
            listOf(File("$tmp.txt"), processLog).forEach {
                if (it.exists()) {
                    it.delete()
                }
            }
        }
    }

    /**
     * prefix のファイル名の連番を探して次の番号のファイル名を作って返す．
     *
     * prefix=page の時，page* が存在しなければ page_000.jpg を，
     * page_123.jpg が存在すれば page_124.jpg を返す．みたいな．
     */
    @Suppress("SameParameterValue")
    private fun generateFilename(prefix: String): String {
        // 連番は桁数を跨いで比較する必要があるため、ファイル名の文字列順ではなく数値で最大を採る。
        val num = File(setting.workDirectory)
            .list { _, name -> name.startsWith(prefix) }
            ?.mapNotNull { FILENAME_NUMBER_REGEX.find(it)?.value?.toIntOrNull() }
            ?.maxOrNull()
            ?.let { it + 1 }
            ?: 0
        return "${prefix}_%03d.jpg".format(num)
    }

    fun getNameList(): List<Triple<String, String, String>> {
        return comicStorage.all.map {
            Triple(it.id, it.author, it.title)
        }
    }

    fun setNameList(nameList: List<Triple<String, String, String>>) {
        nameList.forEach { (id, author, title) ->
            comicStorage[id]?.let {
                it.author = author
                it.title = title
            }
        }
    }
}
