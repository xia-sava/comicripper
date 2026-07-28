package to.sava.comicripper.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.oshai.kotlinlogging.KotlinLogging
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

/**
 * ウィンドウのサイズと位置。位置が負値のときは未設定を表し、配置をプラットフォームへ任せる。
 */
class WindowGeometry(width: Double, height: Double) {
    var width by mutableStateOf(width)
    var height by mutableStateOf(height)
    var posX by mutableStateOf(-1.0)
    var posY by mutableStateOf(-1.0)
}

/**
 * アプリの設定。
 *
 * 各項目は Compose の snapshot state で保持するため、変更は画面へ自動的に伝わる。
 * 永続化は [SettingData] との相互変換で行なう（項目は 1:1 対応）。
 */
class Setting {
    val mainWindow = WindowGeometry(960.0, 720.0)
    val detailWindow = WindowGeometry(1280.0, 720.0)
    val cutterWindow = WindowGeometry(1280.0, 720.0)
    val settingWindow = WindowGeometry(720.0, 720.0)

    var cutterLeftPercent by mutableStateOf(15.0)
    var cutterRightPercent by mutableStateOf(48.5)

    var workDirectory by mutableStateOf("C:/tmp/C")
    var storeDirectory by mutableStateOf("C:/tmp/B")
    var googleBookApi by mutableStateOf("https://www.googleapis.com/books/v1/volumes?q=isbn:")
    var yodobashiSearchUrl by mutableStateOf("https://www.yodobashi.com/?word=")
    var tesseractExe by mutableStateOf("C:/Program Files/Tesseract-OCR/tesseract.exe")

    /** アプリデータの置き場所。テストからは一時ディレクトリに差し替える。 */
    internal var dataDirectory: File = defaultDataDirectory()

    /** JSON形式の設定ファイル。 */
    internal val settingFile get() = File(dataDirectory, "setting.json")

    /** ホームディレクトリ直下に置いていた頃のJSON設定ファイル。存在すれば現行の置き場所へ自動移行する。 */
    private val homeJsonSettingFile get() = File(System.getProperty("user.home") + "/.comicripper.json")

    /** 旧Properties形式の設定ファイル。存在すれば起動時に読み込んでJSON形式へ自動移行する。 */
    private val legacySettingFile get() = File(System.getProperty("user.home") + "/.comicripper")

    /**
     * 構造ファイルを読み書きするディレクトリ。[fixStructureDirectory] で固定するまでは作業ディレクトリに従う。
     */
    private var fixedStructureDirectory: String? = null

    /**
     * 構造ファイルの置き場所をこの時点の作業ディレクトリに固定する。
     * 実行中に作業ディレクトリを変更しても、読み込んだ内容を別のディレクトリへ書き出さないようにする
     * （作業ディレクトリの変更は次回起動時に反映される）。
     */
    fun fixStructureDirectory() {
        fixedStructureDirectory = workDirectory
    }

    /** JSON形式の構造ファイル。 */
    val structureFile get() = File("${fixedStructureDirectory ?: workDirectory}/.comicripperStructure.json")

    /** 旧Properties形式の構造ファイル。存在すれば起動時に読み込んでJSON形式へ自動移行する。 */
    val legacyStructureFile get() = File("${fixedStructureDirectory ?: workDirectory}/.comicripperStructure")

    private fun toData() = SettingData(
        mainWindowWidth = mainWindow.width,
        mainWindowHeight = mainWindow.height,
        mainWindowPosX = mainWindow.posX,
        mainWindowPosY = mainWindow.posY,
        detailWindowWidth = detailWindow.width,
        detailWindowHeight = detailWindow.height,
        detailWindowPosX = detailWindow.posX,
        detailWindowPosY = detailWindow.posY,
        cutterWindowWidth = cutterWindow.width,
        cutterWindowHeight = cutterWindow.height,
        cutterWindowPosX = cutterWindow.posX,
        cutterWindowPosY = cutterWindow.posY,
        settingWindowWidth = settingWindow.width,
        settingWindowHeight = settingWindow.height,
        settingWindowPosX = settingWindow.posX,
        settingWindowPosY = settingWindow.posY,
        cutterLeftPercent = cutterLeftPercent,
        cutterRightPercent = cutterRightPercent,
        workDirectory = workDirectory,
        storeDirectory = storeDirectory,
        googleBookApiUrl = googleBookApi,
        yodobashiSearchUrl = yodobashiSearchUrl,
        tesseractExe = tesseractExe,
    )

    private fun applyData(data: SettingData) {
        mainWindow.apply {
            width = data.mainWindowWidth
            height = data.mainWindowHeight
            posX = data.mainWindowPosX
            posY = data.mainWindowPosY
        }
        detailWindow.apply {
            width = data.detailWindowWidth
            height = data.detailWindowHeight
            posX = data.detailWindowPosX
            posY = data.detailWindowPosY
        }
        cutterWindow.apply {
            width = data.cutterWindowWidth
            height = data.cutterWindowHeight
            posX = data.cutterWindowPosX
            posY = data.cutterWindowPosY
        }
        settingWindow.apply {
            width = data.settingWindowWidth
            height = data.settingWindowHeight
            posX = data.settingWindowPosX
            posY = data.settingWindowPosY
        }
        cutterLeftPercent = data.cutterLeftPercent
        cutterRightPercent = data.cutterRightPercent
        workDirectory = data.workDirectory
        storeDirectory = data.storeDirectory
        googleBookApi = data.googleBookApiUrl
        yodobashiSearchUrl = data.yodobashiSearchUrl
        tesseractExe = data.tesseractExe
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

    private fun applyLegacyProperties(props: Properties) {
        val numbers: List<Pair<String, (Double) -> Unit>> = listOf(
            "mainWindowWidth" to { it -> mainWindow.width = it },
            "mainWindowHeight" to { it -> mainWindow.height = it },
            "mainWindowPosX" to { it -> mainWindow.posX = it },
            "mainWindowPosY" to { it -> mainWindow.posY = it },
            "detailWindowWidth" to { it -> detailWindow.width = it },
            "detailWindowHeight" to { it -> detailWindow.height = it },
            "detailWindowPosX" to { it -> detailWindow.posX = it },
            "detailWindowPosY" to { it -> detailWindow.posY = it },
            "cutterWindowWidth" to { it -> cutterWindow.width = it },
            "cutterWindowHeight" to { it -> cutterWindow.height = it },
            "cutterWindowPosX" to { it -> cutterWindow.posX = it },
            "cutterWindowPosY" to { it -> cutterWindow.posY = it },
            "settingWindowWidth" to { it -> settingWindow.width = it },
            "settingWindowHeight" to { it -> settingWindow.height = it },
            "settingWindowPosX" to { it -> settingWindow.posX = it },
            "settingWindowPosY" to { it -> settingWindow.posY = it },
            "cutterLeftPercent" to { it -> cutterLeftPercent = it },
            "cutterRightPercent" to { it -> cutterRightPercent = it },
        )
        val texts: List<Pair<String, (String) -> Unit>> = listOf(
            "workDirectory" to { it -> workDirectory = it },
            "storeDirectory" to { it -> storeDirectory = it },
            "googleBookApiUrl" to { it -> googleBookApi = it },
            "YodobashiSearchUrl" to { it -> yodobashiSearchUrl = it },
            "TesseractExe" to { it -> tesseractExe = it },
        )
        numbers.forEach { (name, assign) ->
            props.getProperty(name)?.toDoubleOrNull()?.let(assign)
        }
        texts.forEach { (name, assign) ->
            props.getProperty(name)?.let(assign)
        }
    }
}
