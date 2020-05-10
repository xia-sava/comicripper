package to.sava.comicripper.repository

import javafx.embed.swing.SwingFXUtils
import javafx.geometry.Rectangle2D
import javafx.scene.SnapshotParameters
import javafx.scene.image.ImageView
import javafx.scene.image.WritableImage
import javafx.scene.paint.Color
import to.sava.comicripper.model.Comic
import to.sava.comicripper.model.Setting
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam





class ComicRepository {
    companion object {
        const val COVER_FRONT_PREFIX = "coverA"
        const val COVER_ALL_PREFIX = "coverF"
        const val COVER_BELT_PREFIX = "coverS"
        const val PAGE_PREFIX = "page"
    }

    suspend fun listComicFiles(): Sequence<Comic> {
        val dir = File(Setting.workDirectory)
        return sequence {
            dir.listFiles { file -> file.name.endsWith(".jpg") }?.map {
                val url = it.toURI().toURL().toString()
                val comic = Comic(it.name).apply {
                    when {
                        it.name.startsWith(COVER_FRONT_PREFIX) -> coverFront = url
                        it.name.startsWith(COVER_ALL_PREFIX) -> coverAll = url
                        it.name.startsWith(COVER_BELT_PREFIX) -> coverBelt = url
                        it.name.startsWith(PAGE_PREFIX) -> pagesProperty.add(url)
                    }
                }
                yield(comic)
            } ?: listOf()
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
        val outputFile = File("${Setting.workDirectory}/${generateFilename(COVER_FRONT_PREFIX)}")
        ImageIO.write(newImage, "jpeg", outputFile)

        comic.coverFront = outputFile.toURI().toURL().toString()
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
}