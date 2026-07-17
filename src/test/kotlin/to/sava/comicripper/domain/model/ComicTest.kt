package to.sava.comicripper.domain.model

import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
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
    inner class `changeFlow発火` {

        @Test
        fun `author変更でchangeFlowが発火する`() = runTest {
            val comic = Comic()
            var fired = false
            val job = launch {
                comic.changeFlow.first()
                fired = true
            }
            yield()

            comic.author = "新著者"
            job.join()

            assertTrue(fired)
        }

        @Test
        fun `title変更でchangeFlowが発火する`() = runTest {
            val comic = Comic()
            var fired = false
            val job = launch {
                comic.changeFlow.first()
                fired = true
            }
            yield()

            comic.title = "新タイトル"
            job.join()

            assertTrue(fired)
        }

        @Test
        fun `addFileでchangeFlowが発火する`() = runTest {
            val comic = Comic()
            var count = 0
            val job = launch {
                comic.changeFlow.first()
                count++
            }
            yield()

            comic.addFile("page_000.jpg")
            job.join()

            assertEquals(1, count)
        }

        @Test
        fun `removeFileでchangeFlowが発火する`() = runTest {
            val comic = Comic()
            comic.addFile("page_000.jpg")
            var count = 0
            val job = launch {
                comic.changeFlow.first()
                count++
            }
            yield()

            comic.removeFile("page_000.jpg")
            job.join()

            assertEquals(1, count)
        }

        @Test
        fun `addFiles複数でchangeFlowが発火する`() = runTest {
            val comic = Comic()
            var count = 0
            val job = launch {
                comic.changeFlow.first()
                count++
            }
            yield()

            val src = Comic()
            src.addFile("page_000.jpg")
            src.addFile("page_001.jpg")
            comic.merge(src)
            job.join()

            assertEquals(1, count)
        }

        @Test
        fun `removeFiles複数でchangeFlowが発火する`() = runTest {
            val comic = Comic()
            comic.addFile("page_000.jpg")
            comic.addFile("page_001.jpg")
            var count = 0
            val job = launch {
                comic.changeFlow.first()
                count++
            }
            yield()

            comic.removeFiles(listOf("page_000.jpg", "page_001.jpg"))
            job.join()

            assertEquals(1, count)
        }

        @Test
        fun `Jobをcancelすると発火しない`() = runTest {
            val comic = Comic()
            var fired = false
            val job = launch {
                comic.changeFlow.collect {
                    fired = true
                }
            }
            job.cancel()

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
