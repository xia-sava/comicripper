package to.sava.comicripper.infrastructure.repository

import to.sava.comicripper.domain.model.Comic
import to.sava.comicripper.model.Setting
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

object ComicTestHelper {

    fun disableImageLoaders() {
        Comic.thumbnailLoader = { null }
        Comic.fullSizeImageLoader = { null }
    }

    fun resetImageLoaders() {
        Comic.resetImageLoaders()
    }

    fun createDummyJpeg(filename: String): File {
        val file = File("${Setting.workDirectory}/$filename")
        val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
        ImageIO.write(image, "jpg", file)
        return file
    }

    fun setupDirectories(workDir: File, storeDir: File) {
        workDir.mkdirs()
        storeDir.mkdirs()
        Setting.workDirectory = workDir.absolutePath
        Setting.storeDirectory = storeDir.absolutePath
    }
}
