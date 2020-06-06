package to.sava.comicripper.model

import javafx.beans.property.Property
import javafx.beans.property.SimpleDoubleProperty
import javafx.beans.property.SimpleIntegerProperty
import javafx.beans.property.SimpleStringProperty
import java.io.File
import java.lang.Exception
import java.util.*

object Setting {
    val mainWindowWidthProperty = SimpleDoubleProperty(960.0)
    var mainWindowWidth: Double
        get() = mainWindowWidthProperty.value
        set(value) {
            mainWindowWidthProperty.value = value
        }

    val mainWindowHeightProperty = SimpleDoubleProperty(720.0)
    var mainWindowHeight: Double
        get() = mainWindowHeightProperty.value
        set(value) {
            mainWindowHeightProperty.value = value
        }

    val mainWindowPosXProperty = SimpleDoubleProperty(-1.0)
    var mainWindowPosX: Double
        get() = mainWindowPosXProperty.value
        set(value) {
            mainWindowPosXProperty.value = value
        }

    val mainWindowPosYProperty = SimpleDoubleProperty(-1.0)
    var mainWindowPosY: Double
        get() = mainWindowPosYProperty.value
        set(value) {
            mainWindowPosYProperty.value = value
        }

    val detailWindowWidthProperty = SimpleDoubleProperty(1280.0)
    var detailWindowWidth: Double
        get() = detailWindowWidthProperty.value
        set(value) {
            detailWindowWidthProperty.value = value
        }

    val detailWindowHeightProperty = SimpleDoubleProperty(720.0)
    var detailWindowHeight: Double
        get() = detailWindowHeightProperty.value
        set(value) {
            detailWindowHeightProperty.value = value
        }

    val detailWindowPosXProperty = SimpleDoubleProperty(-1.0)
    var detailWindowPosX: Double
        get() = detailWindowPosXProperty.value
        set(value) {
            detailWindowPosXProperty.value = value
        }

    val detailWindowPosYProperty = SimpleDoubleProperty(-1.0)
    var detailWindowPosY: Double
        get() = detailWindowPosYProperty.value
        set(value) {
            detailWindowPosYProperty.value = value
        }

    val cutterWindowWidthProperty = SimpleDoubleProperty(1280.0)
    var cutterWindowWidth: Double
        get() = cutterWindowWidthProperty.value
        set(value) {
            cutterWindowWidthProperty.value = value
        }

    val cutterWindowHeightProperty = SimpleDoubleProperty(720.0)
    var cutterWindowHeight: Double
        get() = cutterWindowHeightProperty.value
        set(value) {
            cutterWindowHeightProperty.value = value
        }

    val cutterWindowPosXProperty = SimpleDoubleProperty(-1.0)
    var cutterWindowPosX: Double
        get() = cutterWindowPosXProperty.value
        set(value) {
            cutterWindowPosXProperty.value = value
        }

    val cutterWindowPosYProperty = SimpleDoubleProperty(-1.0)
    var cutterWindowPosY: Double
        get() = cutterWindowPosYProperty.value
        set(value) {
            cutterWindowPosYProperty.value = value
        }

    val settingWindowWidthProperty = SimpleDoubleProperty(720.0)
    var settingWindowWidth: Double
        get() = settingWindowWidthProperty.value
        set(value) {
            settingWindowWidthProperty.value = value
        }

    val settingWindowHeightProperty = SimpleDoubleProperty(720.0)
    var settingWindowHeight: Double
        get() = settingWindowHeightProperty.value
        set(value) {
            settingWindowHeightProperty.value = value
        }

    val settingWindowPosXProperty = SimpleDoubleProperty(-1.0)
    var settingWindowPosX: Double
        get() = settingWindowPosXProperty.value
        set(value) {
            settingWindowPosXProperty.value = value
        }

    val settingWindowPosYProperty = SimpleDoubleProperty(-1.0)
    var settingWindowPosY: Double
        get() = settingWindowPosYProperty.value
        set(value) {
            settingWindowPosYProperty.value = value
        }

    val cutterLeftPercentProperty = SimpleDoubleProperty(15.0)
    var cutterLeftPercent: Double
        get() = cutterLeftPercentProperty.value
        set(value) {
            cutterLeftPercentProperty.value = value
        }

    val cutterRightPercentProperty = SimpleDoubleProperty(48.5)
    var cutterRightPercent: Double
        get() = cutterRightPercentProperty.value
        set(value) {
            cutterRightPercentProperty.value = value
        }

    val workDirectoryProperty = SimpleStringProperty("C:/tmp/C")
    var workDirectory: String
        get() = workDirectoryProperty.value
        set(value) {
            workDirectoryProperty.value = value
        }

    val storeDirectoryProperty = SimpleStringProperty("C:/tmp/B")
    var storeDirectory: String
        get() = storeDirectoryProperty.value
        set(value) {
            storeDirectoryProperty.value = value
        }

    val googleBookApiUrlProperty = SimpleStringProperty("https://www.googleapis.com/books/v1/volumes?q=isbn:")
    var googleBookApi: String
        get() = googleBookApiUrlProperty.value
        set(value) {
            googleBookApiUrlProperty.value = value
        }

    val YodobashiSearchUrlProperty = SimpleStringProperty("https://www.yodobashi.com/?word=")
    var YodobashiSearchUrl: String
        get() = YodobashiSearchUrlProperty.value
        set(value) {
            YodobashiSearchUrlProperty.value = value
        }

    val TesseractExeProperty = SimpleStringProperty("C:/Program Files/Tesseract-OCR/tesseract.exe")
    var TesseractExe: String
        get() = TesseractExeProperty.value
        set(value) {
            TesseractExeProperty.value = value
        }

    private val settingFile get() = File(System.getProperty("user.home") + "/.comicripper")
    val structureFile get() = File("${workDirectory}/.comicripperStructure")

    private val propFields get() = javaClass.declaredFields.filter { it.name.endsWith("Property") }

    fun save() {
        val props = Properties()
        propFields.forEach {
            props.setProperty(
                it.name.replace("Property", ""),
                (it.get(this) as Property<*>).value.toString()
            )
        }
        settingFile.outputStream().use {
            props.store(it, "comicripper")
        }
    }

    fun load(): Boolean {
        return try {
            val props = Properties()
            settingFile.inputStream().use {
                props.load(it)
            }
            propFields.forEach {
                val field = it.get(this)
                val value = props.getProperty(it.name.replace("Property", ""))
                value?.let {
                    when (field) {
                        is SimpleStringProperty -> field.value = value
                        is SimpleIntegerProperty -> field.value = value.toInt()
                        is SimpleDoubleProperty -> field.value = value.toDouble()
                    }
                }
            }
            true
        } catch (ex: Exception) {
            false
        }
    }
}
