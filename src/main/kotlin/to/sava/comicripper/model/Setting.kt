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

    var workDirectory = "C:/tmp/C"

    private val settingFile get() = File(System.getProperty("user.home") + "/.comicripper")

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
            props.load(settingFile.inputStream())
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