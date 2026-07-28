package to.sava.comicripper.infrastructure.image

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import to.sava.comicripper.model.Setting
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.nio.file.Path

class ComicImageStoreTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var setting: Setting

    @BeforeEach
    fun setup() {
        setting = Setting()
        setting.workDirectory = tempDir.toFile().absolutePath
    }

    private fun image(width: Int = 1, height: Int = 1) = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

    /** 読み取り回数を数えつつ、要求されたファイル名に応じた画像を返す読み取り口。 */
    private class CountingReader(private val images: Map<String, BufferedImage>) : (File) -> BufferedImage? {
        var count = 0
            private set

        override fun invoke(file: File): BufferedImage? {
            count++
            return images[file.name]
        }
    }

    @Nested
    inner class `原寸画像の保持` {

        @Test
        fun `同じファイル名の2回目は保持したものを返す`() {
            val reader = CountingReader(mapOf("page_000.jpg" to image()))
            val store = ComicImageStore(setting, reader)

            store.getFullSizeImage("page_000.jpg")
            store.getFullSizeImage("page_000.jpg")

            assertEquals(1, reader.count)
        }

        @Test
        fun `容量を超えると最も長く使われていないものが追い出される`() {
            val filenames = (0..10).map { "page_%03d.jpg".format(it) }
            val reader = CountingReader(filenames.associateWith { image() })
            val store = ComicImageStore(setting, reader)

            // 10件を読んで保持を満杯にし、11件目で最初の1件を追い出す。
            filenames.take(10).forEach { store.getFullSizeImage(it) }
            store.getFullSizeImage(filenames[10])
            val countBeforeReload = reader.count

            store.getFullSizeImage(filenames[0])

            assertEquals(countBeforeReload + 1, reader.count, "追い出された1件は読み直されるはず")
        }

        @Test
        fun `invalidateしたファイルは読み直される`() {
            val reader = CountingReader(mapOf("page_000.jpg" to image()))
            val store = ComicImageStore(setting, reader)
            store.getFullSizeImage("page_000.jpg")

            store.invalidate(listOf("page_000.jpg"))
            store.getFullSizeImage("page_000.jpg")

            assertEquals(2, reader.count)
        }

        @Test
        fun `invalidateしていないファイルは保持したままになる`() {
            val reader = CountingReader(mapOf("page_000.jpg" to image(), "page_001.jpg" to image()))
            val store = ComicImageStore(setting, reader)
            store.getFullSizeImage("page_000.jpg")
            store.getFullSizeImage("page_001.jpg")

            store.invalidate(listOf("page_000.jpg"))
            store.getFullSizeImage("page_001.jpg")

            assertEquals(2, reader.count)
        }

        @Test
        fun `読めないファイルは例外になる`() {
            val store = ComicImageStore(setting, CountingReader(emptyMap()))

            assertThrows(IllegalStateException::class.java) { store.getFullSizeImage("page_000.jpg") }
        }
    }

    @Nested
    inner class `サムネイル` {

        @Test
        fun `結果を保持しないので呼ぶたびに読み直す`() {
            val reader = CountingReader(mapOf("page_000.jpg" to image()))
            val store = ComicImageStore(setting, reader)

            store.loadThumbnail("page_000.jpg")
            store.loadThumbnail("page_000.jpg")

            assertEquals(2, reader.count)
        }

        @Test
        fun `上限を超える画像は縦横比を保って縮小される`() {
            val store = ComicImageStore(setting, CountingReader(mapOf("page_000.jpg" to image(1024, 512))))

            val thumbnail = store.loadThumbnail("page_000.jpg")

            assertEquals(256, thumbnail?.width)
            assertEquals(128, thumbnail?.height)
        }

        @Test
        fun `上限に収まる画像はそのまま返す`() {
            val store = ComicImageStore(setting, CountingReader(mapOf("page_000.jpg" to image(100, 50))))

            val thumbnail = store.loadThumbnail("page_000.jpg")

            assertEquals(100, thumbnail?.width)
            assertEquals(50, thumbnail?.height)
        }

        @Test
        fun `読めないファイルはnullを返す`() {
            val store = ComicImageStore(setting, CountingReader(emptyMap()))

            assertNull(store.loadThumbnail("page_000.jpg"))
        }

        @Test
        fun `読み取りが失敗してもnullを返す`() {
            val store = ComicImageStore(setting, { throw IOException("読めない") })

            assertNull(store.loadThumbnail("page_000.jpg"))
        }
    }

    @Nested
    inner class `isLandscape` {

        @Test
        fun `横長ならtrue`() {
            val store = ComicImageStore(setting, CountingReader(mapOf("coverF_000.jpg" to image(200, 100))))

            assertTrue(store.isLandscape("coverF_000.jpg"))
        }

        @Test
        fun `縦長ならfalse`() {
            val store = ComicImageStore(setting, CountingReader(mapOf("coverF_000.jpg" to image(100, 200))))

            assertFalse(store.isLandscape("coverF_000.jpg"))
        }

        @Test
        fun `正方形はfalse`() {
            val store = ComicImageStore(setting, CountingReader(mapOf("coverF_000.jpg" to image(100, 100))))

            assertFalse(store.isLandscape("coverF_000.jpg"))
        }
    }

    @Test
    fun `作業ディレクトリ配下のファイルを読む`() {
        var requested: File? = null
        val store = ComicImageStore(setting, { requested = it; image() })

        store.loadThumbnail("page_000.jpg")

        assertEquals(File(tempDir.toFile(), "page_000.jpg").absolutePath, requested?.absolutePath)
    }
}
