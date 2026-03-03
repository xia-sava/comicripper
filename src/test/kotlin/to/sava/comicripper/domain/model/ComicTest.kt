package to.sava.comicripper.domain.model

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ComicTest {

    @BeforeEach
    fun setup() {
        Comic.thumbnailLoader = { null }
        Comic.fullSizeImageLoader = { null }
    }

    @AfterEach
    fun tearDown() {
        Comic.resetImageLoaders()
    }

    @Nested
    inner class `リスナー発火` {

        @Test
        fun `author変更でlistenerが発火する`() {
            val comic = Comic()
            var fired = false
            comic.addListener { fired = true }

            comic.author = "新著者"

            assertTrue(fired)
        }

        @Test
        fun `title変更でlistenerが発火する`() {
            val comic = Comic()
            var fired = false
            comic.addListener { fired = true }

            comic.title = "新タイトル"

            assertTrue(fired)
        }

        @Test
        fun `addFileでlistenerが発火する`() {
            val comic = Comic()
            var count = 0
            comic.addListener { count++ }

            comic.addFile("page_000.jpg")

            assertEquals(1, count)
        }

        @Test
        fun `removeFileでlistenerが発火する`() {
            val comic = Comic()
            comic.addFile("page_000.jpg")
            var count = 0
            comic.addListener { count++ }

            comic.removeFile("page_000.jpg")

            assertEquals(1, count)
        }

        @Test
        fun `addFiles複数でlistenerが1回だけ発火する`() {
            val comic = Comic()
            var count = 0
            comic.addListener { count++ }

            val src = Comic()
            src.addFile("page_000.jpg")
            src.addFile("page_001.jpg")
            comic.merge(src)

            assertEquals(1, count)
        }

        @Test
        fun `removeFiles複数でlistenerが1回だけ発火する`() {
            val comic = Comic()
            comic.addFile("page_000.jpg")
            comic.addFile("page_001.jpg")
            var count = 0
            comic.addListener { count++ }

            comic.removeFiles(listOf("page_000.jpg", "page_001.jpg"))

            assertEquals(1, count)
        }

        @Test
        fun `removeListenerで解除後は発火しない`() {
            val comic = Comic()
            var fired = false
            val listener: (Comic) -> Unit = { fired = true }
            comic.addListener(listener)
            comic.removeListener(listener)

            comic.author = "変更"

            assertFalse(fired)
        }
    }

    @Nested
    inner class `mergeとmergeConflict` {

        @Test
        fun `mergeで相手のファイルが自分に移動する`() {
            val dst = Comic()
            val src = Comic("page_000.jpg")
            src.addFile("page_001.jpg")

            dst.merge(src)

            assertTrue(dst.files.contains("page_000.jpg"))
            assertTrue(dst.files.contains("page_001.jpg"))
        }

        @Test
        fun `merge後に相手のファイルは空になる`() {
            val dst = Comic()
            val src = Comic("page_000.jpg")

            dst.merge(src)

            assertTrue(src.files.isEmpty())
        }

        @Test
        fun `mergeConflictでcoverFull同士はtrue`() {
            val a = Comic("coverF_000.jpg")
            val b = Comic("coverF_001.jpg")

            assertTrue(a.mergeConflict(b))
        }

        @Test
        fun `mergeConflictでcoverFull対pageはfalse`() {
            val a = Comic("coverF_000.jpg")
            val b = Comic("page_000.jpg")

            assertFalse(a.mergeConflict(b))
        }

        @Test
        fun `mergeConflictでcoverAlbum同士はtrue`() {
            val a = Comic("coverA_000.jpg")
            val b = Comic("coverA_001.jpg")

            assertTrue(a.mergeConflict(b))
        }

        @Test
        fun `mergeConflictでcoverStrip同士はtrue`() {
            val a = Comic("coverS_000.jpg")
            val b = Comic("coverS_001.jpg")

            assertTrue(a.mergeConflict(b))
        }
    }

    @Nested
    inner class `ファイル管理` {

        @Test
        fun `addFileで同種coverが置き換えられる`() {
            val comic = Comic("coverF_000.jpg")
            val replaced = comic.addFile("coverF_001.jpg")

            assertEquals("coverF_000.jpg", replaced)
            assertFalse(comic.files.contains("coverF_000.jpg"))
            assertTrue(comic.files.contains("coverF_001.jpg"))
        }

        @Test
        fun `filesがソート済みで返る`() {
            val comic = Comic()
            comic.addFile("page_002.jpg")
            comic.addFile("page_000.jpg")
            comic.addFile("page_001.jpg")

            assertEquals(listOf("page_000.jpg", "page_001.jpg", "page_002.jpg"), comic.files)
        }
    }
}
