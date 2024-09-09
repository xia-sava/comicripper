package to.sava.comicripper.repository

import javafx.beans.property.SimpleListProperty
import javafx.embed.swing.SwingFXUtils
import javafx.geometry.Rectangle2D
import javafx.scene.SnapshotParameters
import javafx.scene.image.ImageView
import javafx.scene.image.WritableImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.HttpStatusException
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
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.Paths
import java.text.Normalizer
import java.util.*
import java.util.stream.Collectors
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

    fun listFiles(pattern: String): List<Path> {
        val dirPath = Paths.get(Setting.workDirectory)
        val matcher = FileSystems.getDefault().getPathMatcher("glob:$pattern")
        return Files.list(dirPath)
            .filter { path -> Files.isRegularFile(path) && matcher.matches(path.fileName) }
            .collect(Collectors.toList())
    }

    fun addFiles(filenames: List<String>) {
        filenames.forEach { addFile(it) }
    }

    private fun addFile(filename: String) {
        if (filename.startsWith(Comic.COVER_FULL_PREFIX)) {
            Comic(filename).let {
                ComicStorage.add(it)
                ComicStorage.targetId = it.id
            }
        } else {
            val target = ComicStorage.target
            if (target != null) {
                target.addFile(filename)?.let {
                    ComicStorage.add(Comic(it))
                }
            } else {
                ComicStorage.add(Comic(filename))
            }
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
        if (comic.coverAlbum.isNullOrEmpty().not()) {
            File("${Setting.workDirectory}/${comic.coverAlbum}").delete()
        }

        val coverFullImage = checkNotNull(comic.coverFullImage)
        val imageView = ImageView().apply {
            image = coverFullImage
        }
        val imageWidth = coverFullImage.width
        val imageHeight = coverFullImage.height
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
            File("${Setting.workDirectory}/${generateFilename(Comic.COVER_ALBUM_PREFIX)}")
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
                    src.startsWith(Comic.COVER_ALBUM_PREFIX) -> Comic.COVER_ALBUM_PREFIX
                    src.startsWith(Comic.COVER_FULL_PREFIX) -> Comic.COVER_FULL_PREFIX
                    src.startsWith(Comic.COVER_STRIP_PREFIX) -> Comic.COVER_STRIP_PREFIX
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

    suspend fun ocrISBN(comic: Comic): Pair<String, String>? {
        val coverFull = comic.coverFull ?: return null
        val tmp = withContext(Dispatchers.IO) {
            Files.createTempFile(Paths.get(Setting.workDirectory), "_tmp", "")
        }
        return try {
            val cmd =
                """"${Setting.TesseractExe}" "${workFilename(coverFull)}" "$tmp" -l jpn --psm 11"""
            withContext(Dispatchers.IO) {
                Runtime.getRuntime().exec(cmd).waitFor()
            }

            File("$tmp.txt").readText()
                .replace(" ", "")
                .replace("\n", " ")
                .replace("-", "")
                .let { """(978\d{10}|ISBN(?:\d\D*){13})""".toRegex().find(it) }
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

    suspend fun searchISBN(pIsbn: String): Pair<String, String> {
        // ISBN 10桁→13桁変換
        val isbn = if (pIsbn.length == 13) pIsbn else "978$pIsbn"

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
            val a = authors.joinToString("／") {
                normalizeText(it).replace(" ", "")
            }
            val t = normalizeText(title)
                .replace('(', '<').replace(')', '>')
                .replace('[', '<').replace(']', '>')
                .replace('{', '<').replace('}', '>')
                .replace('＜', '<').replace('＞', '>')
                .replace('「', '<').replace('」', '>')
                .replace('〔', '<').replace('〕', '>')
                .replace('【', '<').replace('】', '>')
                .replace('『', '<').replace('』', '>')
                .replace('《', '<').replace('》', '>')
                .replace("""<.*?(\d*).*?>""".toRegex(), "<$1>")
                .replace("""\s+第?\s*(\d+)\s*巻""".toRegex(), " <$1>")
                .replace("""：\s*(\d+)""".toRegex(), " <$1>")
                .replace("<>", "")
                .trimEnd()
                .replace("""\s*<?(\d+)>?[\d<> ]*$""".toRegex(), " ($1)")
            return Pair(a, t)
        }

        // Amazon.com スクレイピング
        try {
            println("Amazon $isbn start")
            Jsoup.connect("https://www.amazon.co.jp/s?k=isbn+$isbn").timeout(10_000).get()
                .select("#search .s-main-slot a[href]").firstOrNull()
                ?.absUrl("href")
                ?.let { Jsoup.connect(it).timeout(10_000).get() }
                ?.let { page ->
                    val title = page.select("#productTitle").first()?.text()
                    val authors =
                        page.select("#bylineInfo .author a")
                            .map { it.text() ?: "" }
                            .filter { it != "" }
                            .filter { t -> listOf("原著", "著者ページ", "検索結果").all { it !in t } }
                            .ifEmpty {
                                page.select("#bylineInfo .author a")
                                    .map { it.text() ?: "" }
                                    .ifEmpty { listOf("作者不明") }
                            }
                    if (title != null) {
                        println("Amazon $isbn done")
                        return normalize(authors, title)
                    }
                }
        } catch (e: HttpStatusException) {
            // ステータスエラーは握り潰しちゃうよ
            println("Amazon $isbn error: ${e.message}")
        }

        // Yodobashi.com スクレイピング
        try {
            println("Yodobashi $isbn start")
            Jsoup.connect("${Setting.YodobashiSearchUrl}$isbn").timeout(10_000).get()
                .takeIf { it.select(".noResult").isEmpty() }
                ?.select(".pListBlock a[href]")?.firstOrNull()
                ?.absUrl("href")
                ?.let { Jsoup.connect(it).timeout(10_000).get() }
                ?.let { page ->
                    val title = page.select("#products_maintitle").first()?.text()
                    val authors = page.select("#js_bookAuthor a")
                        .map { it.text() ?: "" }
                        .ifEmpty { listOf("作者不明") }
                    if (title != null) {
                        println("Yodobashi $isbn done")
                        return normalize(authors, title)
                    }
                }
        } catch (e: HttpStatusException) {
            // ステータスエラーは握り潰しちゃうよ
            println("Yodobashi $isbn error: ${e.message}")
        }

        // Google Book API
        try {
            println("Google $isbn start")
            Json.createReader(
                withContext(Dispatchers.IO) {
                    InputStreamReader(
                        URL("${Setting.googleBookApi}$isbn")
                            .openStream(), "utf-8"
                    ).buffered()
                }
            )
                .readObject()?.let { json ->
                    if (json.getInt("totalItems") > 0) {
                        val info =
                            json.getJsonArray("items")?.getJsonObject(0)?.getJsonObject("volumeInfo")
                        val authors =
                            info?.getJsonArray("authors")?.map { (it as JsonString).string ?: "" }
                        val title = info?.getString("title")
                        if (authors != null && title != null) {
                            println("Google $isbn done")
                            return normalize(authors, title)
                        }
                    }
                }
        } catch (e: HttpStatusException) {
            // ステータスエラーは握り潰しちゃうよ
            println("Google $isbn error: ${e.message}")
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
            ?.maxOrNull()
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
                .associate {
                    val id = it.trimStart('_')
                    val (index, author, title) = props.getProperty(it).split("\t")
                    val comic = Comic().apply {
                        this.id = id
                        this.author = author
                        this.title = title
                    }
                    index.toInt() to comic
                }
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

    fun getNameList(): List<Triple<String, String, String>> {
        return ComicStorage.all.map {
            Triple(it.id, it.author, it.title)
        }
    }

    fun setNameList(nameList: List<Triple<String, String, String>>) {
        nameList.forEach { (id, author, title) ->
            ComicStorage[id]?.let {
                it.author = author
                it.title = title
            }
        }
    }
}

object ComicStorage {
    private val storage = SimpleListProperty<Comic>(observableListOf())
    var targetId: String? = null

    val property get() = storage
    val all get() = storage.value.toList()
    val files get() = storage.value.toList().flatMap { it.files }
    val target get() = targetId?.let { this[it] }

    fun add(vararg comics: Comic) {
        storage.addAll(comics)
    }

    fun remove(vararg comics: Comic) {
        comics.forEach { comic ->
            storage.remove(comic)
        }
    }

    fun removeEmpty() {
        storage.removeAll(all.filter { it.files.isEmpty() }.toSet())
    }

    operator fun get(id: String?): Comic? {
        return id?.let {
            storage.firstOrNull { it.id == id }
        }
    }
}
