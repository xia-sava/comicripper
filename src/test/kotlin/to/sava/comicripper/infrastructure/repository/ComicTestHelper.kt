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

    fun createDummyJpeg(filename: String, workDir: File): File {
        val file = File(workDir, filename)
        val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
        ImageIO.write(image, "jpg", file)
        return file
    }

    fun createDummyJpeg(filename: String, width: Int, height: Int, workDir: File): File {
        val file = File(workDir, filename)
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        ImageIO.write(image, "jpg", file)
        return file
    }

    fun setupDirectories(workDir: File, storeDir: File, setting: Setting) {
        workDir.mkdirs()
        storeDir.mkdirs()
        setting.workDirectory = workDir.absolutePath
        setting.storeDirectory = storeDir.absolutePath
    }
}
