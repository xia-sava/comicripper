package to.sava.comicripper.repository

import javafx.beans.property.SimpleListProperty
import javafx.embed.swing.SwingFXUtils
import javafx.geometry.Rectangle2D
import javafx.scene.SnapshotParameters
import javafx.scene.image.ImageView
import javafx.scene.image.WritableImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import to.sava.comicripper.ext.workFilename
import to.sava.comicripper.model.Comic
import to.sava.comicripper.model.Setting
import tornadofx.observableListOf
import java.awt.image.BufferedImage
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStreamReader
import java.net.URL
import java.nio.file.Files
import java.nio.file.Paths
import java.text.Normalizer
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO
import javax.json.Json
import javax.json.JsonString

class ComicRepository {

    fun reScanFiles(targetComic: Comic? = null) {
        val dir = File(Setting.workDirectory)
        val structuredFiles = ComicStorage.files
        dir.listFiles { file -> file.name.matches(Comic.TARGET_REGEX) }?.map {
            if (it.name !in structuredFiles) {
                val comic = Comic(it.name)
                if (targetComic != null) {
                    if (targetComic.mergeConflict(comic)) {
                        ComicStorage.add(comic)
                    } else {
                        targetComic.merge(comic)
                    }
                } else {
                    ComicStorage.add(comic)
                }
            }
        }
        ComicStorage.all.forEach { comic ->
            comic.removeFiles(comic.files.filter {
                File("${Setting.workDirectory}/$it").exists().not()
            })
            if (comic.files.isEmpty()) {
                ComicStorage.remove(comic)
            }
        }
    }

    fun addFiles(comic: Comic?, filenames: List<String>) {
        filenames.forEach { addFile(comic, it) }
    }

    private fun addFile(comic: Comic?, filename: String) {
        if (filename.startsWith(Comic.COVER_ALL_PREFIX).not() && comic != null) {
            comic.addFile(filename)?.let {
                ComicStorage.add(Comic(it))
            }
        } else {
            ComicStorage.add(Comic(filename))
        }
    }

    fun removeFiles(filenames: List<String>) {
        filenames.forEach(::removeFile)
    }

    private fun removeFile(filename: String) {
        ComicStorage.all.forEach { it.removeFile(filename) }
        ComicStorage.removeEmpty()
    }

    suspend fun cutCover(
        comic: Comic,
        leftPercent: Double,
        rightPercent: Double,
        rightMargin: Double
    ) {
        if (comic.coverFront.isNullOrEmpty().not()) {
            File("${Setting.workDirectory}/${comic.coverFront}").delete()
        }

        val coverAllImage = checkNotNull(comic.coverAllImage)
        val imageView = ImageView().apply {
            image = coverAllImage
        }
        val imageWidth = coverAllImage.width
        val imageHeight = coverAllImage.height
        val leftX = imageWidth * (leftPercent / 100.0)
        val rightX = imageWidth * (rightPercent / 100.0) + rightMargin
        val croppedWidth = rightX - leftX
        val croppedFxImage = WritableImage(croppedWidth.toInt(), imageHeight.toInt())
        val ssParams = SnapshotParameters().apply {
            viewport = Rectangle2D(leftX, 0.0, croppedWidth, imageHeight)
        }
        imageView.snapshot(ssParams, croppedFxImage)
        val croppedSwImage = SwingFXUtils.fromFXImage(croppedFxImage, null)
        val outputImage =
            BufferedImage(croppedWidth.toInt(), imageHeight.toInt(), BufferedImage.OPAQUE)
        outputImage.createGraphics().drawImage(croppedSwImage, 0, 0, null)
        val outputFile =
            File("${Setting.workDirectory}/${generateFilename(Comic.COVER_FRONT_PREFIX)}")
        withContext(Dispatchers.IO) {
            ImageIO.write(outputImage, "jpeg", outputFile)
        }
    }

    fun zipComic(comic: Comic) {
        val zipFilename = File("${Setting.storeDirectory}/${comic.author}/${comic.title}.zip")
        if (zipFilename.exists()) {
            zipFilename.delete()
        }
        File(zipFilename.parent).mkdirs()
        ZipOutputStream(BufferedOutputStream(zipFilename.outputStream())).use { zipStream ->
            var pageNum = 1
            comic.files.forEach { src ->
                val name = when {
                    src.startsWith(Comic.COVER_FRONT_PREFIX) -> Comic.COVER_FRONT_PREFIX
                    src.startsWith(Comic.COVER_ALL_PREFIX) -> Comic.COVER_ALL_PREFIX
                    src.startsWith(Comic.COVER_BELT_PREFIX) -> Comic.COVER_BELT_PREFIX
                    else -> "page_%03d".format(pageNum++)
                } + ".jpg"
                zipStream.putNextEntry(ZipEntry(name))
                zipStream.write(Files.readAllBytes(Paths.get("${Setting.workDirectory}/$src")))
            }
        }
        ComicStorage.remove(comic)
        comic.files
            .map { Paths.get("${Setting.workDirectory}/$it") }
            .forEach { Files.deleteIfExists(it) }
    }

    fun pagesToComic(comic: Comic) {
        ComicStorage.all
            .filter { it.files.size == 1 && it.files.first().startsWith(Comic.PAGE_PREFIX) }
            .forEach {
                comic.merge(it)
                ComicStorage.remove(it)
            }
    }

//    fun releaseFiles(comic: Comic, filenames: List<String>) {
//        filenames.forEach { releaseFile(comic, it) }
//    }

    fun releaseFile(comic: Comic, filename: String) {
        if (filename in comic.files) {
            comic.removeFile(filename)
            ComicStorage.add(Comic(filename))
        }
    }

    fun ocrISBN(comic: Comic): Pair<String, String>? {
        val coverAll = comic.coverAll ?: return null
        val tmp = Files.createTempFile(Paths.get(Setting.workDirectory), "_tmp", "")
        return try {
            val cmd = """"${Setting.TesseractExe}" "${workFilename(coverAll)}" "$tmp" -l jpn """
            Runtime.getRuntime().exec(cmd).waitFor()
            val ocrText = File("$tmp.txt").readText()

            ocrText.let { """((?:978\d{10}|ISBN(?:\d\D?){13}))""".toRegex().find(it) }
                ?.groupValues?.get(1)
                ?.replace("""\D""".toRegex(), "")
                ?.replace("""^(\d{13}).*$""".toRegex(), "$1")
                ?.let { isbn ->
                    searchISBN(isbn)
                }
                ?: Pair("エラー", "ISBN不明")
        } catch (ex: UnsatisfiedLinkError) {
            Pair("エラー", "cant find Tesseract")
        } finally {
            tmp.toFile().delete()
            File("$tmp.txt").let {
                if (it.exists()) {
                    it.delete()
                }
            }
        }
    }

    fun searchISBN(isbn: String): Pair<String, String> {
        // 共通の正規化処理
        fun normalizeText(text: String): String {
            return Normalizer.normalize(text, Normalizer.Form.NFKC)
                .replace(Character.codePointOf("FULLWIDTH TILDE").toChar(), '～')
                .replace(Character.codePointOf("WAVE DASH").toChar(), '～')
                .replace('!', '！').replace('\'', '’')
                .replace('"', '”').replace('%', '％')
                .replace('&', '＆').replace(':', '：')
                .replace('*', '＊').replace('?', '？')
                .replace('?', '／').replace('<', '＜')
                .replace('>', '＞').replace('|', '｜')
                .replace('~', '～').replace('/', '／')
                .replace('\\', '￥')

        }

        fun normalize(authors: Iterable<String>, title: String): Pair<String, String> {
            val a = authors
                .map {
                    normalizeText(it)
                        .replace(" ", "")
                }
            val t = normalizeText(title)
                .replace('(', '<').replace(')', '>')
                .replace('[', '<').replace(']', '>')
                .replace('{', '<').replace('}', '>')
                .replace('「', '<').replace('」', '>')
                .replace('〔', '<').replace('〕', '>')
                .replace('【', '<').replace('】', '>')
                .replace('『', '<').replace('』', '>')
                .replace('《', '<').replace('》', '>')
                .replace("""<.*?(\d*).*?>""".toRegex(), "<$1>")
                .replace("""\s+(\d+)巻""".toRegex(), " <$1>")
                .replace("<>", "")
                .trimEnd()
                .replace("""\s*<?(\d+)>?$""".toRegex(), " ($1)")
            return Pair(a.joinToString("／"), t)
        }

        // Yodobashi.com スクレイピング
        Jsoup.connect("${Setting.YodobashiSearchUrl}$isbn").timeout(10_000).get()
            .select(".pListBlock a[href]").firstOrNull()
            ?.absUrl("href")
            ?.let { Jsoup.connect(it).timeout(10_000).get() }
            ?.let { page ->
                val title = page.select("#products_maintitle")?.first()?.text()
                val authors = page.select("#js_bookAuthor a")?.map { it.text() ?: "" }
                if (authors != null && title != null) {
                    return normalize(authors, title)
                }
            }

        // Amazon.com スクレイピング
        Jsoup.connect("https://www.amazon.co.jp/s?k=isbn+$isbn").timeout(10_000).get()
            .select("#search .s-main-slot a[href]").firstOrNull()
            ?.absUrl("href")
            ?.let { Jsoup.connect(it).timeout(10_000).get() }
            ?.let { page ->
                val title = page.select("#productTitle")?.first()?.text()
                val authors =
                    page.select("#bylineInfo .author a.contributorNameID")?.map { it.text() ?: "" }
                        ?.ifEmpty {
                            page.select("#bylineInfo .author a")?.map { it.text() ?: "" }
                                ?: listOf("作者不明")
                        }
                if (authors != null && title != null) {
                    return normalize(authors, title)
                }
            }

        // Google Book API
        Json.createReader(
            InputStreamReader(
                URL("${Setting.googleBookApi}$isbn")
                    .openStream(), "utf-8"
            ).buffered()
        )
            .readObject()?.let { json ->
                if (json.getInt("totalItems") > 0) {
                    val info =
                        json.getJsonArray("items")?.getJsonObject(0)?.getJsonObject("volumeInfo")
                    val authors =
                        info?.getJsonArray("authors")?.map { (it as JsonString).string ?: "" }
                    val title = info?.getString("title")
                    if (authors != null && title != null) {
                        return normalize(authors, title)
                    }
                }
            }
        return Pair("ISBN", isbn)
    }

    /**
     * prefix のファイル名の連番を探して次の番号のファイル名を作って返す．
     *
     * prefix=page の時，page* が存在しなければ page_000.jpg を，
     * page_123.jpg が存在すれば page_124.jpg を返す．みたいな．
     */
    @Suppress("SameParameterValue")
    private fun generateFilename(prefix: String): String {
        val num = File(Setting.workDirectory)
            .list { _, name -> name.startsWith(prefix) }
            ?.max()
            ?.let { filename ->
                Regex("""\d+""").find(filename)?.value?.toInt()?.let { it + 1 }
            } ?: 0
        return "${prefix}_%03d.jpg".format(num)
    }

    fun saveStructure() {
        val props = Properties()
        ComicStorage.all.forEachIndexed { index, comic ->
            props.setProperty(
                "_${comic.id}",
                listOf("$index", comic.author, comic.title).joinToString("\t")
            )
            comic.files.forEach {
                props.setProperty(it, comic.id)
            }
        }
        Setting.structureFile.outputStream().use {
            props.store(it, "comicripperStructure")
        }
    }

    fun loadStructure(): Boolean {
        if (!Setting.structureFile.exists()) {
            return false
        }
        return try {
            val props = Properties()
            Setting.structureFile.inputStream().use {
                props.load(it)
            }
            props.propertyNames().toList().map { it as String }
                .filter { it.startsWith("_") }
                .map {
                    val id = it.trimStart('_')
                    val (index, author, title) = props.getProperty(it).split("\t")
                    val comic = Comic().apply {
                        this.id = id
                        this.author = author
                        this.title = title
                    }
                    index.toInt() to comic
                }
                .toMap()
                .toSortedMap()
                .forEach {
                    ComicStorage.add(it.value)
                }
            props.propertyNames().toList()
                .map { it as String }
                .filterNot { it.startsWith("_") }
                .sorted()
                .forEach { filename ->
                    if (File("${Setting.workDirectory}/$filename").exists()) {
                        val comicId = props.getProperty(filename)
                        val baseComic = ComicStorage[comicId]
                        if (baseComic != null) {
                            baseComic.addFile(filename)
                        } else {
                            ComicStorage.add(Comic(filename))
                        }
                    }
                }
            ComicStorage.all.filter { it.files.isEmpty() }.forEach {
                ComicStorage.remove(it)
            }
            true
        } catch (ex: Exception) {
            false
        }
    }
}

object ComicStorage {
    private val storage = SimpleListProperty<Comic>(observableListOf())

    val property get() = storage
    val all get() = storage.value.toList()
    val files get() = storage.value.toList().flatMap { it.files }

    fun add(vararg comics: Comic) {
        storage.addAll(comics)
    }

    fun remove(vararg comics: Comic) {
        comics.forEach { comic ->
            storage.remove(comic)
        }
    }

    fun removeEmpty() {
        storage.removeAll(all.filter { it.files.isEmpty() })
    }

    operator fun get(id: String?): Comic? {
        return id?.let {
            storage.firstOrNull { it.id == id }
        }
    }
}
