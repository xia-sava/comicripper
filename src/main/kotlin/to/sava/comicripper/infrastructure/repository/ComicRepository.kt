package to.sava.comicripper.infrastructure.repository

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import to.sava.comicripper.domain.model.Comic
import to.sava.comicripper.ext.workFilename
import to.sava.comicripper.model.Setting
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStreamReader
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.text.Normalizer
import java.util.*
import java.util.stream.Collectors
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.imageio.ImageIO

private val logger = KotlinLogging.logger {}

private val structureJson = Json { prettyPrint = true }

/** JSON永続化用の構造ファイルのComic1件分のスナップショット。 */
@Serializable
private data class ComicStructureEntry(
    val id: String,
    val author: String,
    val title: String,
    val files: List<String>,
)

@Serializable
private data class ComicStructureData(
    val comics: List<ComicStructureEntry> = emptyList(),
)

/** ファイル名・アーカイブ名に使えない半角記号を対応する全角文字へ置き換えるための変換表。 */
private val FULLWIDTH_CHAR_MAP: Map<Char, Char> = mapOf(
    Character.codePointOf("FULLWIDTH TILDE").toChar() to '～',
    Character.codePointOf("WAVE DASH").toChar() to '～',
    '!' to '！',
    '\'' to '’',
    '"' to '”',
    '%' to '％',
    '&' to '＆',
    ':' to '：',
    '*' to '＊',
    '?' to '？',
    '<' to '＜',
    '>' to '＞',
    '|' to '｜',
    '~' to '～',
    '/' to '／',
    '\\' to '￥',
)

/** タイトル中の各種括弧を `<` `>` へ統一するための変換表。 */
private val BRACKET_CHAR_MAP: Map<Char, Char> = mapOf(
    '(' to '<', ')' to '>',
    '[' to '<', ']' to '>',
    '{' to '<', '}' to '>',
    '＜' to '<', '＞' to '>',
    '「' to '<', '」' to '>',
    '〔' to '<', '〕' to '>',
    '【' to '<', '】' to '>',
    '『' to '<', '』' to '>',
    '《' to '<', '》' to '>',
)

class ComicRepository(private val setting: Setting, private val comicStorage: ComicStorage) {

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

    @Suppress("unused")
    fun listFiles(pattern: String): List<Path> {
        val dirPath = Paths.get(setting.workDirectory)
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
    }

    suspend fun cutCover(
        comic: Comic,
        leftPercent: Double,
        rightPercent: Double,
        rightMargin: Double
    ) {
        if (comic.coverAlbum.isNullOrEmpty().not()) {
            File("${setting.workDirectory}/${comic.coverAlbum}").delete()
        }

        withContext(Dispatchers.IO) {
            val coverFull = checkNotNull(comic.coverFull)
            val coverFullImage = checkNotNull(ImageIO.read(File(workFilename(coverFull, setting.workDirectory)))) {
                "no image for $coverFull"
            }
            val imageWidth = coverFullImage.width.toDouble()
            val imageHeight = coverFullImage.height
            val leftX = imageWidth * (leftPercent / 100.0)
            val rightX = imageWidth * (rightPercent / 100.0) + rightMargin
            val croppedWidth = rightX - leftX

            val outputImage = BufferedImage(croppedWidth.toInt(), imageHeight, BufferedImage.TYPE_INT_RGB)
            outputImage.createGraphics().apply {
                color = Color.WHITE
                fillRect(0, 0, outputImage.width, outputImage.height)
                drawImage(coverFullImage, -leftX.toInt(), 0, null)
                dispose()
            }
            val outputFile = File("${setting.workDirectory}/${generateFilename(Comic.COVER_ALBUM_PREFIX)}")
            ImageIO.write(outputImage, "jpeg", outputFile)
        }
    }

    fun zipComic(comic: Comic) {
        val zipFilename = File("${setting.storeDirectory}/${comic.author}/${comic.title}.zip")
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

//    fun releaseFiles(comic: Comic, filenames: List<String>) {
//        filenames.forEach { releaseFile(comic, it) }
//    }

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
        return try {
            withContext(Dispatchers.IO) {
                ProcessBuilder(
                    setting.TesseractExe,
                    workFilename(coverFull, setting.workDirectory),
                    tmp.toString(),
                    "-l", "jpn",
                    "--psm", "11"
                )
                    .inheritIO()
                    .start()
                    .waitFor()
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
        } catch (_: UnsatisfiedLinkError) {
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

    // 共通の正規化処理
    internal fun normalizeText(text: String): String {
        val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
        return FULLWIDTH_CHAR_MAP.entries.fold(normalized) { acc, (from, to) -> acc.replace(from, to) }
    }

    internal fun normalize(authors: Iterable<String>, title: String): Pair<String, String> {
        val a = authors.joinToString("／") {
            normalizeText(it).replace(" ", "")
        }
        val bracketsUnified = BRACKET_CHAR_MAP.entries.fold(normalizeText(title)) { acc, (from, to) -> acc.replace(from, to) }
        val t = bracketsUnified
            .replace("""<.*?(\d*).*?>""".toRegex(), "<$1>")
            .replace("""\s+第?\s*(\d+)\s*巻""".toRegex(), " <$1>")
            .replace("""：\s*(\d+)""".toRegex(), " <$1>")
            .replace("<>", "")
            .trimEnd()
            .replace("""\s*<?(\d+)>?[\d<> ]*$""".toRegex(), " ($1)")
        return Pair(a, t)
    }

    suspend fun searchISBN(pIsbn: String): Pair<String, String> {
        // ISBN 10桁→13桁変換
        val isbn = if (pIsbn.length == 13) pIsbn else "978$pIsbn"

        // Amazon.com スクレイピング
        try {
            logger.info { "Amazon $isbn start" }
            Jsoup.connect("https://www.amazon.co.jp/s?k=isbn+$isbn").timeout(10_000).get()
                .select("#search .s-main-slot a[href]").firstOrNull()
                ?.absUrl("href")
                ?.let { Jsoup.connect(it).timeout(10_000).get() }
                ?.let { page ->
                    val title = page.select("#productTitle").first()?.text()
                    val authors =
                        page.select("#bylineInfo .author a")
                            .map { it.text() }
                            .filter { it != "" }
                            .filter { t -> listOf("原著", "著者ページ", "検索結果").all { it !in t } }
                            .ifEmpty {
                                page.select("#bylineInfo .author a")
                                    .map { it.text() }
                                    .ifEmpty { listOf("作者不明") }
                            }
                    if (title != null) {
                        logger.info { "Amazon $isbn done" }
                        return normalize(authors, title)
                    }
                }
        } catch (e: HttpStatusException) {
            // ステータスエラーは握り潰しちゃうよ
            logger.warn(e) { "Amazon $isbn error" }
        }

        // Yodobashi.com スクレイピング
        try {
            logger.info { "Yodobashi $isbn start" }
            Jsoup.connect("${setting.YodobashiSearchUrl}$isbn").timeout(10_000).get()
                .takeIf { it.select(".noResult").isEmpty() }
                ?.select(".pListBlock a[href]")?.firstOrNull()
                ?.absUrl("href")
                ?.let { Jsoup.connect(it).timeout(10_000).get() }
                ?.let { page ->
                    val title = page.select("#products_maintitle").first()?.text()
                    val authors = page.select("#js_bookAuthor a")
                        .map { it.text() }
                        .ifEmpty { listOf("作者不明") }
                    if (title != null) {
                        logger.info { "Yodobashi $isbn done" }
                        return normalize(authors, title)
                    }
                }
        } catch (e: HttpStatusException) {
            // ステータスエラーは握り潰しちゃうよ
            logger.warn(e) { "Yodobashi $isbn error" }
        }

        // Google Book API
        try {
            logger.info { "Google $isbn start" }
            val responseText = withContext(Dispatchers.IO) {
                InputStreamReader(
                    URI("${setting.googleBookApi}$isbn")
                        .toURL()
                        .openConnection()
                        .getInputStream(), "utf-8"
                ).buffered().readText()
            }
            val json = Json.parseToJsonElement(responseText).jsonObject
            val totalItems = json["totalItems"]?.jsonPrimitive?.intOrNull ?: 0
            if (totalItems > 0) {
                val info = json["items"]?.jsonArray?.getOrNull(0)?.jsonObject
                    ?.get("volumeInfo")?.jsonObject
                val authors = info?.get("authors")?.jsonArray?.map { it.jsonPrimitive.content }
                val title = info?.get("title")?.jsonPrimitive?.contentOrNull
                if (authors != null && title != null) {
                    logger.info { "Google $isbn done" }
                    return normalize(authors, title)
                }
            }
        } catch (e: HttpStatusException) {
            // ステータスエラーは握り潰しちゃうよ
            logger.warn(e) { "Google $isbn error" }
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
        val num = File(setting.workDirectory)
            .list { _, name -> name.startsWith(prefix) }
            ?.maxOrNull()
            ?.let { filename ->
                Regex("""\d+""").find(filename)?.value?.toInt()?.let { it + 1 }
            } ?: 0
        return "${prefix}_%03d.jpg".format(num)
    }

    // プロセスが書き込み中に強制終了しても壊れたファイルが残らないよう、
    // 同一ディレクトリの一時ファイルへ書いてから rename で置き換える。
    fun saveStructure() {
        val data = ComicStructureData(
            comics = comicStorage.all.map { comic ->
                ComicStructureEntry(comic.id, comic.author, comic.title, comic.files)
            }
        )
        val text = structureJson.encodeToString(ComicStructureData.serializer(), data)
        val structureFile = setting.structureFile
        val tempFile = File.createTempFile("comicripperStructure", ".tmp", structureFile.absoluteFile.parentFile)
        try {
            tempFile.writeText(text)
            Files.move(
                tempFile.toPath(),
                structureFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            tempFile.delete()
        }
    }

    /**
     * 構造を読み込む。JSON形式ファイルがあればそれを使い、無く旧Properties形式ファイルが
     * あれば読み込んでJSON形式で保存し直し、旧ファイルは `.bak` へリネームして残す。
     */
    fun loadStructure(): Boolean {
        if (setting.structureFile.exists()) {
            return runCatching {
                applyStructureData(structureJson.decodeFromString(ComicStructureData.serializer(), setting.structureFile.readText()))
            }.onFailure { logger.warn(it) { "structure load failed" } }.isSuccess
        }
        if (setting.legacyStructureFile.exists()) {
            return loadLegacyStructureAndMigrate()
        }
        return false
    }

    private fun applyStructureData(data: ComicStructureData) {
        data.comics.forEach { entry ->
            val comic = Comic().apply {
                id = entry.id
                author = entry.author
                title = entry.title
            }
            comicStorage.add(comic)
            entry.files.sorted().forEach { filename ->
                if (File("${setting.workDirectory}/$filename").exists()) {
                    comic.addFile(filename)
                }
            }
        }
        comicStorage.all.filter { it.files.isEmpty() }.forEach { comicStorage.remove(it) }
    }

    private fun loadLegacyStructureAndMigrate(): Boolean {
        val loaded = runCatching {
            val props = Properties()
            setting.legacyStructureFile.inputStream().use { props.load(it) }
            applyLegacyStructureProperties(props)
        }.onFailure { logger.warn(it) { "legacy structure load failed" } }.isSuccess
        if (!loaded) {
            return false
        }
        runCatching {
            saveStructure()
            val backup = File("${setting.legacyStructureFile.path}.bak")
            Files.move(setting.legacyStructureFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }.onFailure { logger.warn(it) { "legacy structure migration failed" } }
        return true
    }

    private fun applyLegacyStructureProperties(props: Properties) {
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
                comicStorage.add(it.value)
            }
        props.propertyNames().toList()
            .map { it as String }
            .filterNot { it.startsWith("_") }
            .sorted()
            .forEach { filename ->
                if (File("${setting.workDirectory}/$filename").exists()) {
                    val comicId = props.getProperty(filename)
                    val baseComic = comicStorage[comicId]
                    if (baseComic != null) {
                        baseComic.addFile(filename)
                    } else {
                        comicStorage.add(Comic(filename))
                    }
                }
            }
        comicStorage.all.filter { it.files.isEmpty() }.forEach {
            comicStorage.remove(it)
        }
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

class ComicStorage {
    private val _storage = MutableStateFlow<List<Comic>>(emptyList())
    val storage: StateFlow<List<Comic>> get() = _storage
    var targetId: String? = null

    val all get() = _storage.value.toList()
    val files get() = _storage.value.flatMap { it.files }
    val target get() = targetId?.let { this[it] }

    fun add(vararg comics: Comic) {
        _storage.update { it + comics }
    }

    fun remove(vararg comics: Comic) {
        _storage.update { list -> list.filterNot { it in comics.toSet() } }
    }

    fun removeEmpty() {
        _storage.update { list -> list.filter { it.files.isNotEmpty() } }
    }

    fun clear() {
        _storage.value = emptyList()
        targetId = null
    }

    operator fun get(id: String?): Comic? {
        return id?.let { targetId ->
            _storage.value.firstOrNull { it.id == targetId }
        }
    }
}
