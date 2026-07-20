package to.sava.comicripper.model

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.*

private val logger = KotlinLogging.logger {}

private val json = Json {
    prettyPrint = true
    // 新しいバージョンで追加されたキーを含むファイルを旧バージョンでも読めるようにする。
    ignoreUnknownKeys = true
}

/**
 * パースに失敗した永続化ファイルを `.broken` へ退避する。
 * デフォルト値での上書き保存が、手修復の余地ごと元ファイルを消してしまうのを防ぐ。
 */
internal fun quarantineBrokenFile(file: File) {
    runCatching {
        Files.move(file.toPath(), File("${file.path}.broken").toPath(), StandardCopyOption.REPLACE_EXISTING)
        logger.warn { "broken file quarantined: ${file.path}.broken" }
    }.onFailure { logger.warn(it) { "failed to quarantine broken file: $file" } }
}

/**
 * アプリデータ（設定・ログ）の既定の置き場所。
 * Windows では %LOCALAPPDATA%/ComicRipper、それ以外では ~/.local/state/ComicRipper。
 * logback.xml のログ出力先と同じ規則で解決する。
 */
private fun defaultDataDirectory(): File =
    System.getenv("LOCALAPPDATA")?.let { File(it, "ComicRipper") }
        ?: File(System.getProperty("user.home"), ".local/state/ComicRipper")

/** JSON永続化用のスナップショット。Settingの各プロパティと1:1対応する。 */
@Serializable
private data class SettingData(
    val mainWindowWidth: Double = 960.0,
    val mainWindowHeight: Double = 720.0,
    val mainWindowPosX: Double = -1.0,
    val mainWindowPosY: Double = -1.0,
    val detailWindowWidth: Double = 1280.0,
    val detailWindowHeight: Double = 720.0,
    val detailWindowPosX: Double = -1.0,
    val detailWindowPosY: Double = -1.0,
    val cutterWindowWidth: Double = 1280.0,
    val cutterWindowHeight: Double = 720.0,
    val cutterWindowPosX: Double = -1.0,
    val cutterWindowPosY: Double = -1.0,
    val settingWindowWidth: Double = 720.0,
    val settingWindowHeight: Double = 720.0,
    val settingWindowPosX: Double = -1.0,
    val settingWindowPosY: Double = -1.0,
    val cutterLeftPercent: Double = 15.0,
    val cutterRightPercent: Double = 48.5,
    val workDirectory: String = "C:/tmp/C",
    val storeDirectory: String = "C:/tmp/B",
    val googleBookApiUrl: String = "https://www.googleapis.com/books/v1/volumes?q=isbn:",
    val yodobashiSearchUrl: String = "https://www.yodobashi.com/?word=",
    val tesseractExe: String = "C:/Program Files/Tesseract-OCR/tesseract.exe",
)

class Setting {
    val mainWindowWidthFlow = MutableStateFlow(960.0)
    var mainWindowWidth: Double
        get() = mainWindowWidthFlow.value
        set(value) {
            mainWindowWidthFlow.value = value
        }

    val mainWindowHeightFlow = MutableStateFlow(720.0)
    var mainWindowHeight: Double
        get() = mainWindowHeightFlow.value
        set(value) {
            mainWindowHeightFlow.value = value
        }

    val mainWindowPosXFlow = MutableStateFlow(-1.0)
    var mainWindowPosX: Double
        get() = mainWindowPosXFlow.value
        set(value) {
            mainWindowPosXFlow.value = value
        }

    val mainWindowPosYFlow = MutableStateFlow(-1.0)
    var mainWindowPosY: Double
        get() = mainWindowPosYFlow.value
        set(value) {
            mainWindowPosYFlow.value = value
        }

    val detailWindowWidthFlow = MutableStateFlow(1280.0)
    var detailWindowWidth: Double
        get() = detailWindowWidthFlow.value
        set(value) {
            detailWindowWidthFlow.value = value
        }

    val detailWindowHeightFlow = MutableStateFlow(720.0)
    var detailWindowHeight: Double
        get() = detailWindowHeightFlow.value
        set(value) {
            detailWindowHeightFlow.value = value
        }

    val detailWindowPosXFlow = MutableStateFlow(-1.0)
    var detailWindowPosX: Double
        get() = detailWindowPosXFlow.value
        set(value) {
            detailWindowPosXFlow.value = value
        }

    val detailWindowPosYFlow = MutableStateFlow(-1.0)
    var detailWindowPosY: Double
        get() = detailWindowPosYFlow.value
        set(value) {
            detailWindowPosYFlow.value = value
        }

    val cutterWindowWidthFlow = MutableStateFlow(1280.0)
    var cutterWindowWidth: Double
        get() = cutterWindowWidthFlow.value
        set(value) {
            cutterWindowWidthFlow.value = value
        }

    val cutterWindowHeightFlow = MutableStateFlow(720.0)
    var cutterWindowHeight: Double
        get() = cutterWindowHeightFlow.value
        set(value) {
            cutterWindowHeightFlow.value = value
        }

    val cutterWindowPosXFlow = MutableStateFlow(-1.0)
    var cutterWindowPosX: Double
        get() = cutterWindowPosXFlow.value
        set(value) {
            cutterWindowPosXFlow.value = value
        }

    val cutterWindowPosYFlow = MutableStateFlow(-1.0)
    var cutterWindowPosY: Double
        get() = cutterWindowPosYFlow.value
        set(value) {
            cutterWindowPosYFlow.value = value
        }

    val settingWindowWidthFlow = MutableStateFlow(720.0)
    var settingWindowWidth: Double
        get() = settingWindowWidthFlow.value
        set(value) {
            settingWindowWidthFlow.value = value
        }

    val settingWindowHeightFlow = MutableStateFlow(720.0)
    var settingWindowHeight: Double
        get() = settingWindowHeightFlow.value
        set(value) {
            settingWindowHeightFlow.value = value
        }

    val settingWindowPosXFlow = MutableStateFlow(-1.0)
    var settingWindowPosX: Double
        get() = settingWindowPosXFlow.value
        set(value) {
            settingWindowPosXFlow.value = value
        }

    val settingWindowPosYFlow = MutableStateFlow(-1.0)
    var settingWindowPosY: Double
        get() = settingWindowPosYFlow.value
        set(value) {
            settingWindowPosYFlow.value = value
        }

    val cutterLeftPercentFlow = MutableStateFlow(15.0)
    var cutterLeftPercent: Double
        get() = cutterLeftPercentFlow.value
        set(value) {
            cutterLeftPercentFlow.value = value
        }

    val cutterRightPercentFlow = MutableStateFlow(48.5)
    var cutterRightPercent: Double
        get() = cutterRightPercentFlow.value
        set(value) {
            cutterRightPercentFlow.value = value
        }

    val workDirectoryFlow = MutableStateFlow("C:/tmp/C")
    var workDirectory: String
        get() = workDirectoryFlow.value
        set(value) {
            workDirectoryFlow.value = value
        }

    val storeDirectoryFlow = MutableStateFlow("C:/tmp/B")
    var storeDirectory: String
        get() = storeDirectoryFlow.value
        set(value) {
            storeDirectoryFlow.value = value
        }

    val googleBookApiUrlFlow = MutableStateFlow("https://www.googleapis.com/books/v1/volumes?q=isbn:")
    var googleBookApi: String
        get() = googleBookApiUrlFlow.value
        set(value) {
            googleBookApiUrlFlow.value = value
        }

    val YodobashiSearchUrlFlow = MutableStateFlow("https://www.yodobashi.com/?word=")
    var YodobashiSearchUrl: String
        get() = YodobashiSearchUrlFlow.value
        set(value) {
            YodobashiSearchUrlFlow.value = value
        }

    val TesseractExeFlow = MutableStateFlow("C:/Program Files/Tesseract-OCR/tesseract.exe")
    var TesseractExe: String
        get() = TesseractExeFlow.value
        set(value) {
            TesseractExeFlow.value = value
        }

    /** アプリデータの置き場所。テストからは一時ディレクトリに差し替える。 */
    internal var dataDirectory: File = defaultDataDirectory()

    /** JSON形式の設定ファイル。 */
    internal val settingFile get() = File(dataDirectory, "setting.json")

    /** ホームディレクトリ直下に置いていた頃のJSON設定ファイル。存在すれば現行の置き場所へ自動移行する。 */
    private val homeJsonSettingFile get() = File(System.getProperty("user.home") + "/.comicripper.json")

    /** 旧Properties形式の設定ファイル。存在すれば起動時に読み込んでJSON形式へ自動移行する。 */
    private val legacySettingFile get() = File(System.getProperty("user.home") + "/.comicripper")

    /** JSON形式の構造ファイル。 */
    val structureFile get() = File("${workDirectory}/.comicripperStructure.json")

    /** 旧Properties形式の構造ファイル。存在すれば起動時に読み込んでJSON形式へ自動移行する。 */
    val legacyStructureFile get() = File("${workDirectory}/.comicripperStructure")

    private fun toData() = SettingData(
        mainWindowWidth = mainWindowWidth,
        mainWindowHeight = mainWindowHeight,
        mainWindowPosX = mainWindowPosX,
        mainWindowPosY = mainWindowPosY,
        detailWindowWidth = detailWindowWidth,
        detailWindowHeight = detailWindowHeight,
        detailWindowPosX = detailWindowPosX,
        detailWindowPosY = detailWindowPosY,
        cutterWindowWidth = cutterWindowWidth,
        cutterWindowHeight = cutterWindowHeight,
        cutterWindowPosX = cutterWindowPosX,
        cutterWindowPosY = cutterWindowPosY,
        settingWindowWidth = settingWindowWidth,
        settingWindowHeight = settingWindowHeight,
        settingWindowPosX = settingWindowPosX,
        settingWindowPosY = settingWindowPosY,
        cutterLeftPercent = cutterLeftPercent,
        cutterRightPercent = cutterRightPercent,
        workDirectory = workDirectory,
        storeDirectory = storeDirectory,
        googleBookApiUrl = googleBookApi,
        yodobashiSearchUrl = YodobashiSearchUrl,
        tesseractExe = TesseractExe,
    )

    private fun applyData(data: SettingData) {
        mainWindowWidth = data.mainWindowWidth
        mainWindowHeight = data.mainWindowHeight
        mainWindowPosX = data.mainWindowPosX
        mainWindowPosY = data.mainWindowPosY
        detailWindowWidth = data.detailWindowWidth
        detailWindowHeight = data.detailWindowHeight
        detailWindowPosX = data.detailWindowPosX
        detailWindowPosY = data.detailWindowPosY
        cutterWindowWidth = data.cutterWindowWidth
        cutterWindowHeight = data.cutterWindowHeight
        cutterWindowPosX = data.cutterWindowPosX
        cutterWindowPosY = data.cutterWindowPosY
        settingWindowWidth = data.settingWindowWidth
        settingWindowHeight = data.settingWindowHeight
        settingWindowPosX = data.settingWindowPosX
        settingWindowPosY = data.settingWindowPosY
        cutterLeftPercent = data.cutterLeftPercent
        cutterRightPercent = data.cutterRightPercent
        workDirectory = data.workDirectory
        storeDirectory = data.storeDirectory
        googleBookApi = data.googleBookApiUrl
        YodobashiSearchUrl = data.yodobashiSearchUrl
        TesseractExe = data.tesseractExe
    }

    // プロセスが書き込み中に強制終了しても壊れたファイルが残らないよう、
    // 同一ディレクトリの一時ファイルへ書いてから rename で置き換える。
    fun save() {
        val text = json.encodeToString(SettingData.serializer(), toData())
        val parentDir = settingFile.absoluteFile.parentFile
        parentDir.mkdirs()
        val tempFile = File.createTempFile("comicripper", ".tmp", parentDir)
        try {
            tempFile.writeText(text)
            Files.move(
                tempFile.toPath(),
                settingFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            tempFile.delete()
        }
    }

    /**
     * 設定を読み込む。現行の置き場所にJSON形式ファイルがあればそれを使う。
     * 無ければ旧来の置き場所（ホームディレクトリ直下のJSON、さらに旧いProperties形式）を順に探し、
     * 見つかれば読み込んで現行の置き場所へ保存し直し、旧ファイルは `.bak` へリネームして残す。
     */
    fun load(): Boolean {
        if (settingFile.isFile) {
            return runCatching {
                applyData(json.decodeFromString(SettingData.serializer(), settingFile.readText()))
            }.onFailure {
                logger.error(it) { "setting load failed" }
                quarantineBrokenFile(settingFile)
            }.isSuccess
        }
        if (homeJsonSettingFile.isFile) {
            return loadHomeJsonAndMigrate()
        }
        if (legacySettingFile.isFile) {
            return loadLegacyAndMigrate()
        }
        return false
    }

    private fun loadHomeJsonAndMigrate(): Boolean {
        val loaded = runCatching {
            applyData(json.decodeFromString(SettingData.serializer(), homeJsonSettingFile.readText()))
        }.onFailure { logger.warn(it) { "home json setting load failed" } }.isSuccess
        if (!loaded) {
            return false
        }
        runCatching {
            save()
            val backup = File("${homeJsonSettingFile.path}.bak")
            Files.move(homeJsonSettingFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }.onFailure { logger.warn(it) { "home json setting migration failed" } }
        return true
    }

    private fun loadLegacyAndMigrate(): Boolean {
        val loaded = runCatching {
            val props = Properties()
            legacySettingFile.inputStream().use { props.load(it) }
            applyLegacyProperties(props)
        }.onFailure { logger.warn(it) { "legacy setting load failed" } }.isSuccess
        if (!loaded) {
            return false
        }
        runCatching {
            save()
            val backup = File("${legacySettingFile.path}.bak")
            Files.move(legacySettingFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }.onFailure { logger.warn(it) { "legacy setting migration failed" } }
        return true
    }

    @Suppress("UNCHECKED_CAST")
    private fun applyLegacyProperties(props: Properties) {
        val legacyFlowEntries: List<Pair<String, MutableStateFlow<*>>> = listOf(
            "mainWindowWidth" to mainWindowWidthFlow,
            "mainWindowHeight" to mainWindowHeightFlow,
            "mainWindowPosX" to mainWindowPosXFlow,
            "mainWindowPosY" to mainWindowPosYFlow,
            "detailWindowWidth" to detailWindowWidthFlow,
            "detailWindowHeight" to detailWindowHeightFlow,
            "detailWindowPosX" to detailWindowPosXFlow,
            "detailWindowPosY" to detailWindowPosYFlow,
            "cutterWindowWidth" to cutterWindowWidthFlow,
            "cutterWindowHeight" to cutterWindowHeightFlow,
            "cutterWindowPosX" to cutterWindowPosXFlow,
            "cutterWindowPosY" to cutterWindowPosYFlow,
            "settingWindowWidth" to settingWindowWidthFlow,
            "settingWindowHeight" to settingWindowHeightFlow,
            "settingWindowPosX" to settingWindowPosXFlow,
            "settingWindowPosY" to settingWindowPosYFlow,
            "cutterLeftPercent" to cutterLeftPercentFlow,
            "cutterRightPercent" to cutterRightPercentFlow,
            "workDirectory" to workDirectoryFlow,
            "storeDirectory" to storeDirectoryFlow,
            "googleBookApiUrl" to googleBookApiUrlFlow,
            "YodobashiSearchUrl" to YodobashiSearchUrlFlow,
            "TesseractExe" to TesseractExeFlow,
        )
        legacyFlowEntries.forEach { (name, flow) ->
            props.getProperty(name)?.let { value ->
                when (flow.value) {
                    is String -> (flow as MutableStateFlow<String>).value = value
                    is Double -> (flow as MutableStateFlow<Double>).value = value.toDouble()
                    is Int -> (flow as MutableStateFlow<Int>).value = value.toInt()
                }
            }
        }
    }
}
