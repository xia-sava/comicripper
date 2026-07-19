package to.sava.comicripper.model

import kotlinx.coroutines.flow.MutableStateFlow
import java.io.File
import java.util.*

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

    internal val settingFile get() = File(System.getProperty("user.home") + "/.comicripper")
    val structureFile get() = File("${workDirectory}/.comicripperStructure")

    private val flowEntries: List<Pair<String, MutableStateFlow<*>>> = listOf(
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

    fun save() {
        val props = Properties()
        flowEntries.forEach { (name, flow) ->
            props.setProperty(name, flow.value.toString())
        }
        settingFile.outputStream().use {
            props.store(it, "comicripper")
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun load(): Boolean {
        return try {
            val props = Properties()
            settingFile.inputStream().use {
                props.load(it)
            }
            flowEntries.forEach { (name, flow) ->
                props.getProperty(name)?.let { value ->
                    when (flow.value) {
                        is String -> (flow as MutableStateFlow<String>).value = value
                        is Double -> (flow as MutableStateFlow<Double>).value = value.toDouble()
                        is Int -> (flow as MutableStateFlow<Int>).value = value.toInt()
                    }
                }
            }
            true
        } catch (_: Exception) {
            false
        }
    }
}
