package to.sava.comicripper.infrastructure.repository

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import to.sava.comicripper.application.di.testModule
import to.sava.comicripper.domain.model.Comic
import to.sava.comicripper.model.Setting
import java.io.File
import java.nio.file.Path

class ComicRepositoryTest : KoinComponent {

    private val repository: ComicRepository by inject()

    @TempDir
    lateinit var tempDir: Path

    private lateinit var workDir: File
    private lateinit var storeDir: File

    @BeforeEach
    fun setup() {
        startKoin {
            modules(testModule)
        }
        workDir = tempDir.resolve("work").toFile()
        storeDir = tempDir.resolve("store").toFile()
        ComicTestHelper.setupDirectories(workDir, storeDir)
        ComicTestHelper.disableImageLoaders()
        ComicStorage.clear()
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
        ComicTestHelper.resetImageLoaders()
        ComicStorage.clear()
    }

    @Nested
    inner class `ファイル振り分け` {

        @Test
        fun `coverF追加で新Comicが作成されtargetに設定される`() {
            val coverF = "coverF_000.jpg"
            ComicTestHelper.createDummyJpeg(coverF)

            repository.addFiles(listOf(coverF))

            assertEquals(1, ComicStorage.all.size)
            val comic = ComicStorage.all.first()
            assertEquals(coverF, comic.coverFull)
            assertEquals(comic.id, ComicStorage.targetId)
        }

        @Test
        fun `coverF後のpage追加はtargetのComicに入る`() {
            val coverF = "coverF_000.jpg"
            val page = "page_000.jpg"
            ComicTestHelper.createDummyJpeg(coverF)
            ComicTestHelper.createDummyJpeg(page)

            repository.addFiles(listOf(coverF, page))

            assertEquals(1, ComicStorage.all.size)
            val comic = ComicStorage.all.first()
            assertTrue(comic.files.contains(coverF))
            assertTrue(comic.files.contains(page))
        }

        @Test
        fun `2つ目のcoverFでtargetが切り替わる`() {
            val coverF1 = "coverF_000.jpg"
            val coverF2 = "coverF_001.jpg"
            ComicTestHelper.createDummyJpeg(coverF1)
            ComicTestHelper.createDummyJpeg(coverF2)

            repository.addFiles(listOf(coverF1))
            val firstId = ComicStorage.targetId

            repository.addFiles(listOf(coverF2))
            val secondId = ComicStorage.targetId

            assertEquals(2, ComicStorage.all.size)
            assertTrue(firstId != secondId)
        }

        @Test
        fun `targetなしでpage追加は単独Comicになる`() {
            val page = "page_000.jpg"
            ComicTestHelper.createDummyJpeg(page)

            repository.addFiles(listOf(page))

            assertEquals(1, ComicStorage.all.size)
            val comic = ComicStorage.all.first()
            assertEquals(1, comic.files.size)
            assertTrue(comic.files.contains(page))
        }

        @Test
        fun `coverSもtargetのComicに入る`() {
            val coverF = "coverF_000.jpg"
            val coverS = "coverS_000.jpg"
            ComicTestHelper.createDummyJpeg(coverF)
            ComicTestHelper.createDummyJpeg(coverS)

            repository.addFiles(listOf(coverF, coverS))

            assertEquals(1, ComicStorage.all.size)
            val comic = ComicStorage.all.first()
            assertTrue(comic.files.contains(coverF))
            assertTrue(comic.files.contains(coverS))
        }

        @Test
        fun `coverAもtargetのComicに入る`() {
            val coverF = "coverF_000.jpg"
            val coverA = "coverA_000.jpg"
            ComicTestHelper.createDummyJpeg(coverF)
            ComicTestHelper.createDummyJpeg(coverA)

            repository.addFiles(listOf(coverF, coverA))

            assertEquals(1, ComicStorage.all.size)
            val comic = ComicStorage.all.first()
            assertTrue(comic.files.contains(coverF))
            assertTrue(comic.files.contains(coverA))
        }
    }

    @Nested
    inner class `タイトル著者の正規化` {

        @Test
        fun `各種括弧が統一される`() {
            val (_, title) = repository.normalize(listOf("著者"), "タイトル(1)")
            assertEquals("タイトル (1)", title)
        }

        @Test
        fun `角括弧も統一される`() {
            val (_, title) = repository.normalize(listOf("著者"), "タイトル[2]")
            assertEquals("タイトル (2)", title)
        }

        @Test
        fun `波括弧も統一される`() {
            val (_, title) = repository.normalize(listOf("著者"), "タイトル{3}")
            assertEquals("タイトル (3)", title)
        }

        @Test
        fun `第n巻の抽出`() {
            val (_, title) = repository.normalize(listOf("著者"), "タイトル 第5巻")
            assertEquals("タイトル (5)", title)
        }

        @Test
        fun `NFKC正規化`() {
            val text = repository.normalizeText("Ａ")
            assertEquals("A", text)
        }

        @Test
        fun `禁止文字の全角化`() {
            val text = repository.normalizeText("test?file")
            assertEquals("test？file", text)
        }

        @Test
        fun `複数著者のスラッシュ区切り`() {
            val (author, _) = repository.normalize(listOf("著者A", "著者B"), "タイトル")
            assertEquals("著者A／著者B", author)
        }
    }

    @Nested
    inner class `構造の保存復元` {

        @Test
        fun `saveしてloadのラウンドトリップ`() {
            val coverF = "coverF_000.jpg"
            val page = "page_000.jpg"
            ComicTestHelper.createDummyJpeg(coverF)
            ComicTestHelper.createDummyJpeg(page)

            repository.addFiles(listOf(coverF, page))
            val originalComic = ComicStorage.all.first()
            originalComic.author = "テスト著者"
            originalComic.title = "テストタイトル"

            repository.saveStructure()
            ComicStorage.clear()
            assertEquals(0, ComicStorage.all.size)

            val loaded = repository.loadStructure()
            assertTrue(loaded)
            assertEquals(1, ComicStorage.all.size)

            val restoredComic = ComicStorage.all.first()
            assertEquals("テスト著者", restoredComic.author)
            assertEquals("テストタイトル", restoredComic.title)
            assertTrue(restoredComic.files.contains(coverF))
            assertTrue(restoredComic.files.contains(page))
        }

        @Test
        fun `存在しないファイルは除外される`() {
            val coverF = "coverF_000.jpg"
            val page = "page_000.jpg"
            ComicTestHelper.createDummyJpeg(coverF)
            ComicTestHelper.createDummyJpeg(page)

            repository.addFiles(listOf(coverF, page))
            repository.saveStructure()

            File("${Setting.workDirectory}/$page").delete()
            ComicStorage.clear()

            repository.loadStructure()
            val comic = ComicStorage.all.first()
            assertTrue(comic.files.contains(coverF))
            assertFalse(comic.files.contains(page))
        }

        @Test
        fun `空Comicは除去される`() {
            val coverF = "coverF_000.jpg"
            ComicTestHelper.createDummyJpeg(coverF)

            repository.addFiles(listOf(coverF))
            repository.saveStructure()

            File("${Setting.workDirectory}/$coverF").delete()
            ComicStorage.clear()

            repository.loadStructure()
            assertEquals(0, ComicStorage.all.size)
        }
    }

    @Nested
    inner class `merge_release操作` {

        @Test
        fun `pagesToComicで単独pageがtargetに集約される`() {
            val coverF = "coverF_000.jpg"
            val page1 = "page_000.jpg"
            val page2 = "page_001.jpg"
            ComicTestHelper.createDummyJpeg(coverF)
            ComicTestHelper.createDummyJpeg(page1)
            ComicTestHelper.createDummyJpeg(page2)

            repository.addFiles(listOf(coverF))
            val target = ComicStorage.all.first()

            ComicStorage.add(Comic(page1))
            ComicStorage.add(Comic(page2))
            assertEquals(3, ComicStorage.all.size)

            repository.pagesToComic(target)

            assertEquals(1, ComicStorage.all.size)
            assertTrue(target.files.contains(coverF))
            assertTrue(target.files.contains(page1))
            assertTrue(target.files.contains(page2))
        }

        @Test
        fun `releaseFileでファイルが切り離され新Comicになる`() {
            val coverF = "coverF_000.jpg"
            val page = "page_000.jpg"
            ComicTestHelper.createDummyJpeg(coverF)
            ComicTestHelper.createDummyJpeg(page)

            repository.addFiles(listOf(coverF, page))
            val comic = ComicStorage.all.first()
            assertEquals(1, ComicStorage.all.size)

            repository.releaseFile(comic, page)

            assertEquals(2, ComicStorage.all.size)
            assertFalse(comic.files.contains(page))
            val released = ComicStorage.all.first { it.id != comic.id }
            assertTrue(released.files.contains(page))
        }
    }

    @Nested
    inner class `reScanFiles` {

        @Test
        fun `ディレクトリのファイルがComicStorageに反映される`() {
            ComicTestHelper.createDummyJpeg("page_000.jpg")
            ComicTestHelper.createDummyJpeg("page_001.jpg")

            repository.reScanFiles()

            assertEquals(2, ComicStorage.all.size)
        }

        @Test
        fun `消えたファイルと空ComicがreScanで除去される`() {
            val coverF = "coverF_000.jpg"
            ComicTestHelper.createDummyJpeg(coverF)
            repository.addFiles(listOf(coverF))
            assertEquals(1, ComicStorage.all.size)

            File("${Setting.workDirectory}/$coverF").delete()
            repository.reScanFiles()

            assertEquals(0, ComicStorage.all.size)
        }

        @Test
        fun `targetComic指定時にマージされる`() {
            val coverF = "coverF_000.jpg"
            ComicTestHelper.createDummyJpeg(coverF)
            repository.addFiles(listOf(coverF))
            val target = ComicStorage.all.first()

            ComicTestHelper.createDummyJpeg("page_000.jpg")
            repository.reScanFiles(target)

            assertEquals(1, ComicStorage.all.size)
            assertTrue(target.files.contains("page_000.jpg"))
        }
    }

    @Nested
    inner class `zipComic` {

        @Test
        fun `ZIP内ファイル名が正規化される`() {
            val coverF = "coverF_000.jpg"
            val page1 = "page_000.jpg"
            val page2 = "page_001.jpg"
            ComicTestHelper.createDummyJpeg(coverF)
            ComicTestHelper.createDummyJpeg(page1)
            ComicTestHelper.createDummyJpeg(page2)

            repository.addFiles(listOf(coverF, page1, page2))
            val comic = ComicStorage.all.first()
            comic.author = "テスト著者"
            comic.title = "テストタイトル"

            repository.zipComic(comic)

            val zipFile = File("${Setting.storeDirectory}/テスト著者/テストタイトル.zip")
            assertTrue(zipFile.exists())

            val zipEntries = java.util.zip.ZipFile(zipFile).use { zip ->
                zip.entries().toList().map { it.name }
            }
            assertTrue(zipEntries.contains("coverF.jpg"))
            assertTrue(zipEntries.contains("page_001.jpg"))
            assertTrue(zipEntries.contains("page_002.jpg"))
        }

        @Test
        fun `ZIP作成後に元ファイルが削除される`() {
            val coverF = "coverF_000.jpg"
            val page = "page_000.jpg"
            ComicTestHelper.createDummyJpeg(coverF)
            ComicTestHelper.createDummyJpeg(page)

            repository.addFiles(listOf(coverF, page))
            val comic = ComicStorage.all.first()
            comic.author = "著者"
            comic.title = "タイトル"

            repository.zipComic(comic)

            assertFalse(File("${Setting.workDirectory}/$coverF").exists())
            assertFalse(File("${Setting.workDirectory}/$page").exists())
        }

        @Test
        fun `ZIP作成後にComicStorageから除去される`() {
            val coverF = "coverF_000.jpg"
            ComicTestHelper.createDummyJpeg(coverF)

            repository.addFiles(listOf(coverF))
            val comic = ComicStorage.all.first()
            comic.author = "著者"
            comic.title = "タイトル"

            repository.zipComic(comic)

            assertEquals(0, ComicStorage.all.size)
        }
    }
}
