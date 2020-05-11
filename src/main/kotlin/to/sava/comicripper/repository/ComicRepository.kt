package to.sava.comicripper.repository

import javafx.beans.property.SimpleListProperty
import javafx.embed.swing.SwingFXUtils
import javafx.geometry.Rectangle2D
import javafx.scene.SnapshotParameters
import javafx.scene.image.ImageView
import javafx.scene.image.WritableImage
import kotlinx.coroutines.yield
import to.sava.comicripper.model.Comic
import to.sava.comicripper.model.Setting
import tornadofx.observableListOf
import java.awt.image.BufferedImage
import java.io.File
import java.util.*
import javax.imageio.ImageIO

class ComicRepository {

    suspend fun loadFiles() {
        scanFiles {
            ComicStorage.add(it)
        }
    }

    suspend fun reScanFiles(rootComic: Comic) {
        scanFiles {
            if (!rootComic.mergeConflict(it)) {
                rootComic.merge(it)
            } else {
                ComicStorage.add(it)
            }
        }
    }

    private suspend inline fun scanFiles(block: (Comic) -> Unit) {
        val dir = File(Setting.workDirectory)
        val structuredFiles = ComicStorage.files
        dir.listFiles { file -> file.name.endsWith(".jpg") }?.map {
            if (it.name !in structuredFiles) {
                block(Comic(it.name))
            }
            yield()
        }
    }

    fun cutCover(comic: Comic, leftPercent: Double, rightPercent: Double, rightMargin: Double) {
        val coverAllImage = checkNotNull(comic.coverAllImage)
        val imageView = ImageView().apply {
            image = coverAllImage
        }
        val imageWidth = coverAllImage.width
        val imageHeight = coverAllImage.height
        val leftX = imageWidth * (leftPercent / 100.0)
        val rightX = imageWidth * (rightPercent / 100.0) + rightMargin
        val newWidth = rightX - leftX
        val outputImage = WritableImage(newWidth.toInt(), imageHeight.toInt())
        val ssParams = SnapshotParameters().apply {
            viewport = Rectangle2D(leftX, 0.0, newWidth, imageHeight)
        }
        imageView.snapshot(ssParams, outputImage)
        val awtImage = BufferedImage(newWidth.toInt(), imageHeight.toInt(), BufferedImage.TYPE_INT_RGB)
        val newImage = SwingFXUtils.fromFXImage(outputImage, awtImage)
        val outputFile = File("${Setting.workDirectory}/${generateFilename(Comic.COVER_FRONT_PREFIX)}")
        ImageIO.write(newImage, "jpeg", outputFile)

        comic.coverFront = outputFile.name
    }

    /**
     * prefix のファイル名の連番を探して次の番号のファイル名を作って返す．
     *
     * prefix=page の時，page* が存在しなければ page_000.jpg を，
     * page_123.jpg が存在すれば page_124.jpg を返す．みたいな．
     */
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
        ComicStorage.all.forEach { comic ->
            props.setProperty("_${comic.id}", "${comic.author}\t${comic.title}")
            comic.files.forEach {
                props.setProperty(it, comic.id)
            }
        }
        Setting.structureFile.outputStream().use {
            props.store(it, "comicripperStructure")
        }
    }

    suspend fun loadStructure(): Boolean {
        if (!Setting.structureFile.exists()) {
            return false
        }
        return try {
            val props = Properties()
            Setting.structureFile.inputStream().use {
                props.load(it)
            }
            props.propertyNames().toList().map { it as String }.filter { it.startsWith("_") }.forEach {
                val id = it.trimStart('_')
                val (author, title) = props.getProperty(it).split("\t")
                val comic = Comic().apply {
                    this.id = id
                    this.author = author
                    this.title = title
                }
                ComicStorage.add(comic)
            }
            props.propertyNames().toList().map { it as String }.filterNot { it.startsWith("_") }.forEach { filename ->
                val comic = Comic(filename)
                val comicId = props.getProperty(filename)
                val baseComic = ComicStorage[comicId]
                if (baseComic != null) {
                    baseComic.merge(comic)
                } else {
                    ComicStorage.add(comic)
                }
                yield()
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

    fun delete(vararg comics: Comic) {
        comics.forEach { comic ->
            storage.remove(comic)
        }
    }

    operator fun get(id: String?): Comic? {
        return id?.let {
            storage.firstOrNull() { it.id == id }
        }
    }
}
