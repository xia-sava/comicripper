package to.sava.comicripper.domain.model

import androidx.compose.runtime.snapshots.Snapshot
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import java.awt.image.BufferedImage

/** snapshot state の検証ケース。[mutate] で変更し、[read] で観測する対象を指す。 */
private class Case(
    val name: String,
    val mutate: (Comic) -> Unit,
    val read: (Comic) -> Any?,
)

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
    inner class `snapshot stateとしての保持` {

        /**
         * 取得済みスナップショットから変更が見えないことで、プロパティが snapshot state で
         * 保持されている（= Compose が変更を検知して再コンポーズできる）ことを確かめる。
         */
        @TestFactory
        fun `プロパティの変更は取得済みスナップショットには見えない`(): List<DynamicTest> = listOf(
            Case("author", { it.author = "新著者" }, { it.author }),
            Case("title", { it.title = "新タイトル" }, { it.title }),
            Case("addFile", { it.addFile("page_001.jpg") }, { it.files }),
            Case("removeFile", { it.removeFile("page_000.jpg") }, { it.files }),
            Case("addFiles", { it.addFiles(listOf("page_001.jpg", "page_002.jpg")) }, { it.files }),
            Case("removeFiles", { it.removeFiles(listOf("page_000.jpg")) }, { it.files }),
            Case("merge", { it.merge(Comic("page_001.jpg")) }, { it.files }),
        ).map { case ->
            DynamicTest.dynamicTest(case.name) {
                val comic = Comic("page_000.jpg")
                val before = case.read(comic)

                val snapshot = Snapshot.takeSnapshot()
                try {
                    case.mutate(comic)

                    assertEquals(before, snapshot.enter { case.read(comic) }, "取得済みスナップショットには見えないはず")
                    assertNotEquals(before, case.read(comic), "現在の値としては見えるはず")
                } finally {
                    snapshot.dispose()
                }
            }
        }

        @Test
        fun `imageRevisionの変更も取得済みスナップショットには見えない`() {
            val comic = Comic("page_000.jpg")
            val before = comic.imageRevision

            val snapshot = Snapshot.takeSnapshot()
            try {
                comic.invalidateImages()

                assertEquals(before, snapshot.enter { comic.imageRevision })
                assertEquals(before + 1, comic.imageRevision)
            } finally {
                snapshot.dispose()
            }
        }
    }

    @Nested
    inner class `サムネイルの読み込み` {

        @Test
        fun `loadThumbnailは結果を保持しないので呼ぶたびにローダーへ問い合わせる`() {
            var loadCount = 0
            Comic.thumbnailLoader = { loadCount++; BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB) }
            val comic = Comic("page_000.jpg")

            comic.loadThumbnail("page_000.jpg")
            comic.loadThumbnail("page_000.jpg")

            assertEquals(2, loadCount)
        }

        @Test
        fun `ファイル追加でサムネイルは読まれない`() {
            var loadCount = 0
            Comic.thumbnailLoader = { loadCount++; BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB) }

            Comic("page_000.jpg").addFile("page_001.jpg")

            assertEquals(0, loadCount, "画像の読み込みは表示側の要求時まで起こらないはず")
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

    @Nested
    inner class `フルサイズ画像キャッシュ` {

        private fun dummyImage() = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)

        @Test
        fun `同じファイル名の2回目はキャッシュを使いローダーを再呼び出ししない`() {
            var loadCount = 0
            Comic.fullSizeImageLoader = { loadCount++; dummyImage() }
            val comic = Comic("page_000.jpg")

            comic.getFullSizeImage("page_000.jpg")
            comic.getFullSizeImage("page_000.jpg")

            assertEquals(1, loadCount)
        }

        @Test
        fun `容量を超えると最も長くアクセスされていないものが追い出される`() {
            var loadCount = 0
            Comic.fullSizeImageLoader = { loadCount++; dummyImage() }
            val comic = Comic()
            val filenames = (0 until 11).map { "page_%03d.jpg".format(it) }
            filenames.forEach { comic.addFile(it) }

            // page_000〜page_009 の10件をロードしてキャッシュを満杯にする。
            filenames.take(10).forEach { comic.getFullSizeImage(it) }
            // 11件目のロードで、最初にロードした page_000 が追い出されるはず。
            comic.getFullSizeImage(filenames[10])
            val countBeforeReload = loadCount

            comic.getFullSizeImage(filenames[0])

            assertEquals(countBeforeReload + 1, loadCount, "追い出された page_000 は再ロードされるはず")
        }

        @Test
        fun `invalidateImagesでキャッシュが捨てられ再ロードされる`() {
            var loadCount = 0
            Comic.fullSizeImageLoader = { loadCount++; dummyImage() }
            val comic = Comic("page_000.jpg")
            comic.getFullSizeImage("page_000.jpg")

            comic.invalidateImages()
            comic.getFullSizeImage("page_000.jpg")

            assertEquals(2, loadCount)
        }

        @Test
        fun `removeFileでキャッシュからも削除され再ロードされる`() {
            var loadCount = 0
            Comic.fullSizeImageLoader = { loadCount++; dummyImage() }
            val comic = Comic("page_000.jpg")
            comic.getFullSizeImage("page_000.jpg")
            comic.removeFile("page_000.jpg")
            comic.addFile("page_000.jpg")

            comic.getFullSizeImage("page_000.jpg")

            assertEquals(2, loadCount)
        }
    }
}
