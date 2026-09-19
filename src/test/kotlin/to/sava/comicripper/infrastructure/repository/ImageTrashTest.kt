package to.sava.comicripper.infrastructure.repository

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
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

class ImageTrashTest : KoinComponent {

    private val repository: ComicRepository by inject()
    private val setting: Setting by inject()
    private val comicStorage: ComicStorage by inject()

    @TempDir
    lateinit var tempDir: Path

    private lateinit var workDir: File
    private lateinit var trash: ImageTrash

    /** OS のごみ箱へ送った画像のファイル名。実際のごみ箱には送らず、消して記録するだけにする。 */
    private val sentToOsTrash = mutableListOf<String>()

    private fun fakeMoveToOsTrash(file: File): Boolean {
        sentToOsTrash += file.name
        return file.delete()
    }

    @BeforeEach
    fun setup() {
        startKoin {
            modules(testModule)
        }
        workDir = tempDir.resolve("work").toFile()
        ComicTestHelper.setupDirectories(workDir, tempDir.resolve("store").toFile(), setting)
        comicStorage.clear()
        trash = ImageTrash(setting, comicStorage, repository, moveToOsTrash = ::fakeMoveToOsTrash)
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
        comicStorage.clear()
    }

    /** [filenames] の画像を作り、先頭の表紙のコミックとして一覧へ入れる。 */
    private fun comicWith(vararg filenames: String): Comic {
        filenames.forEach { ComicTestHelper.createDummyJpeg(it, workDir) }
        repository.addFiles(filenames.toList())
        return comicStorage.all.first { filenames.first() in it.files }
    }

    private fun trashedFiles(): List<File> =
        setting.trashDirectory.walkTopDown().filter { it.isFile }.toList()

    @Nested
    inner class `delete` {

        @Test
        fun `画像を退避先へ移して構成から外す`() {
            val comic = comicWith("coverF_000.jpg", "page_001.jpg")

            assertTrue(trash.delete(comic, "page_001.jpg"))

            assertFalse(File(workDir, "page_001.jpg").exists())
            assertEquals(listOf("page_001.jpg"), trashedFiles().map { it.name })
            assertFalse("page_001.jpg" in comic.files)
        }

        @Test
        fun `同じ名前の画像を何度消しても退避先で重ならない`() {
            // スキャンし直すと同じ名前の画像がまた作られる。
            val comic = comicWith("coverF_000.jpg", "page_001.jpg")
            trash.delete(comic, "page_001.jpg")
            ComicTestHelper.createDummyJpeg("page_001.jpg", workDir)
            repository.addFiles(listOf("page_001.jpg"))

            assertTrue(trash.delete(comic, "page_001.jpg"))

            assertEquals(2, trashedFiles().size)
        }

        @Test
        fun `ディスクに無い画像は退避できない`() {
            val comic = comicWith("coverF_000.jpg", "page_001.jpg")
            File(workDir, "page_001.jpg").delete()

            assertFalse(trash.delete(comic, "page_001.jpg"))
        }
    }

    @Nested
    inner class `undo` {

        @Test
        fun `最後に消した画像を元の場所と元のコミックへ戻す`() {
            val comic = comicWith("coverF_000.jpg", "page_001.jpg")
            trash.delete(comic, "page_001.jpg")

            val result = trash.undo()

            assertEquals(UndoResult.Restored(comic, "page_001.jpg"), result)
            assertTrue(File(workDir, "page_001.jpg").exists())
            assertTrue("page_001.jpg" in comic.files)
            assertTrue(trashedFiles().isEmpty())
        }

        @Test
        fun `新しく消したものから順に戻す`() {
            val comic = comicWith("coverF_000.jpg", "page_001.jpg", "page_002.jpg")
            trash.delete(comic, "page_001.jpg")
            trash.delete(comic, "page_002.jpg")

            val first = trash.undo()
            val second = trash.undo()

            assertEquals(UndoResult.Restored(comic, "page_002.jpg"), first)
            assertEquals(UndoResult.Restored(comic, "page_001.jpg"), second)
        }

        @Test
        fun `取り消す削除が無ければ何もしない`() {
            assertEquals(UndoResult.NothingToUndo, trash.undo())
        }

        @Test
        fun `最後の1枚を消して一覧から外れたコミックは作者名と題名ごと一覧へ戻す`() {
            val comic = comicWith("coverF_000.jpg")
            comic.author = "著者A"
            comic.title = "題名A"
            trash.delete(comic, "coverF_000.jpg")
            assertFalse(comic in comicStorage.all)

            trash.undo()

            assertTrue(comic in comicStorage.all)
            assertEquals("著者A", comic.author)
            assertEquals("題名A", comic.title)
            assertEquals(listOf("coverF_000.jpg"), comic.files)
        }

        @Test
        fun `画像を残したまま一覧から外れたコミックへは戻さず画像だけのコミックにする`() {
            // ZIP 作成を終えたコミックにあたる。
            val comic = comicWith("coverF_000.jpg", "page_001.jpg")
            trash.delete(comic, "page_001.jpg")
            comicStorage.remove(comic)

            val result = trash.undo()

            val restored = (result as UndoResult.Restored).comic
            assertTrue(restored !== comic)
            assertEquals(listOf("page_001.jpg"), restored.files)
            assertTrue(restored in comicStorage.all)
            assertFalse("page_001.jpg" in comic.files)
        }

        @Test
        fun `元の場所に同じ名前のファイルがあれば上書きせずに戻せなかったとする`() {
            val comic = comicWith("coverF_000.jpg", "page_001.jpg")
            trash.delete(comic, "page_001.jpg")
            val newer = ComicTestHelper.createDummyJpeg("page_001.jpg", 2, 2, workDir)
            val newerLength = newer.length()

            val result = trash.undo()

            assertEquals(UndoResult.Failed("page_001.jpg"), result)
            assertEquals(newerLength, newer.length())
            assertEquals(1, trashedFiles().size)
        }

        @Test
        fun `戻せなかった削除は履歴から外して次の取り消しで前の削除を戻す`() {
            val comic = comicWith("coverF_000.jpg", "page_001.jpg", "page_002.jpg")
            trash.delete(comic, "page_001.jpg")
            trash.delete(comic, "page_002.jpg")
            ComicTestHelper.createDummyJpeg("page_002.jpg", workDir)
            trash.undo()

            val result = trash.undo()

            assertEquals(UndoResult.Restored(comic, "page_001.jpg"), result)
        }

        @Test
        fun `同じ種類の表紙ができていればそちらを別のコミックへ出して戻す`() {
            // 表紙を消した後に切り出し直した場合にあたる。表紙は種類ごとに1枚しか持てない。
            val comic = comicWith("coverF_000.jpg", "coverA_000.jpg")
            trash.delete(comic, "coverA_000.jpg")
            ComicTestHelper.createDummyJpeg("coverA_001.jpg", workDir)
            comic.addFile("coverA_001.jpg")

            trash.undo()

            assertEquals("coverA_000.jpg", comic.coverAlbum)
            assertTrue(comicStorage.all.any { it !== comic && "coverA_001.jpg" in it.files })
        }
    }

    @Nested
    inner class `purge` {

        /** 前回までの起動で退避したまま残っている画像を作る。 */
        private fun trashedInPreviousSession(filename: String): File =
            File(setting.trashDirectory, "previous").also { it.mkdirs() }.let { directory ->
                ComicTestHelper.createDummyJpeg(filename, directory)
            }

        @Test
        fun `前回までに退避した画像をごみ箱へ送って退避先を片付ける`() {
            trashedInPreviousSession("page_001.jpg")

            trash.purge()

            assertEquals(listOf("page_001.jpg"), sentToOsTrash)
            assertFalse(setting.trashDirectory.exists())
        }

        @Test
        fun `取り消せる画像はごみ箱へ送らない`() {
            // 起動時の片付けが終わる前に削除した場合にあたる。
            val comic = comicWith("coverF_000.jpg", "page_001.jpg")
            trash.delete(comic, "page_001.jpg")

            trash.purge()

            assertTrue(sentToOsTrash.isEmpty())
            assertEquals(UndoResult.Restored(comic, "page_001.jpg"), trash.undo())
        }

        @Test
        fun `ごみ箱へ送れなかった画像は退避先に残す`() {
            val file = trashedInPreviousSession("page_001.jpg")
            val unavailable = ImageTrash(setting, comicStorage, repository, moveToOsTrash = { false })

            unavailable.purge()

            assertTrue(file.exists())
        }

        @Test
        fun `purgeAllは取り消せる画像もごみ箱へ送り履歴を捨てる`() {
            val comic = comicWith("coverF_000.jpg", "page_001.jpg")
            trash.delete(comic, "page_001.jpg")

            trash.purgeAll()

            assertEquals(listOf("page_001.jpg"), sentToOsTrash)
            assertFalse(setting.trashDirectory.exists())
            assertEquals(UndoResult.NothingToUndo, trash.undo())
        }
    }
}
