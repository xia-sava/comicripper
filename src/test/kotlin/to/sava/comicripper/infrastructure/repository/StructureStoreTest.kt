package to.sava.comicripper.infrastructure.repository

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import to.sava.comicripper.application.di.testModule
import to.sava.comicripper.model.Setting
import java.io.File
import java.nio.file.Path

class StructureStoreTest : KoinComponent {

    private val structureStore: StructureStore by inject()
    private val repository: ComicRepository by inject()
    private val setting: Setting by inject()
    private val comicStorage: ComicStorage by inject()

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
        ComicTestHelper.setupDirectories(workDir, storeDir, setting)
        comicStorage.clear()
    }

    @AfterEach
    fun tearDown() {
        stopKoin()
        comicStorage.clear()
    }


    @Test
    fun `saveしてloadのラウンドトリップ`() {
        val coverF = "coverF_000.jpg"
        val page = "page_000.jpg"
        ComicTestHelper.createDummyJpeg(coverF, workDir)
        ComicTestHelper.createDummyJpeg(page, workDir)

        repository.addFiles(listOf(coverF, page))
        val originalComic = comicStorage.all.first()
        originalComic.author = "テスト著者"
        originalComic.title = "テストタイトル"

        structureStore.save()
        comicStorage.clear()
        assertEquals(0, comicStorage.all.size)

        val loaded = structureStore.load()
        assertTrue(loaded)
        assertEquals(1, comicStorage.all.size)

        val restoredComic = comicStorage.all.first()
        assertEquals("テスト著者", restoredComic.author)
        assertEquals("テストタイトル", restoredComic.title)
        assertTrue(restoredComic.files.contains(coverF))
        assertTrue(restoredComic.files.contains(page))
    }

    @Test
    fun `存在しないファイルは除外される`() {
        val coverF = "coverF_000.jpg"
        val page = "page_000.jpg"
        ComicTestHelper.createDummyJpeg(coverF, workDir)
        ComicTestHelper.createDummyJpeg(page, workDir)

        repository.addFiles(listOf(coverF, page))
        structureStore.save()

        File("${setting.workDirectory}/$page").delete()
        comicStorage.clear()

        structureStore.load()
        val comic = comicStorage.all.first()
        assertTrue(comic.files.contains(coverF))
        assertFalse(comic.files.contains(page))
    }

    @Test
    fun `空Comicは除去される`() {
        val coverF = "coverF_000.jpg"
        ComicTestHelper.createDummyJpeg(coverF, workDir)

        repository.addFiles(listOf(coverF))
        structureStore.save()

        File("${setting.workDirectory}/$coverF").delete()
        comicStorage.clear()

        structureStore.load()
        assertEquals(0, comicStorage.all.size)
    }

    @Test
    fun `複数コミックを読み込んでも記載順とファイル構成が保たれる`() {
        // 一覧の並び順は構造ファイルの記載順で決まる。取り違えや取りこぼしが起きないことを見る。
        val comicCount = 8
        val pagesPerComic = 12
        repeat(comicCount) { comicIndex ->
            // 先頭の coverF が新しいコミックを起こし、後続のページがそこへ束ねられる。
            val filenames = listOf("coverF_%03d.jpg".format(comicIndex)) +
                (0 until pagesPerComic).map { "page_%03d.jpg".format(comicIndex * 100 + it) }
            filenames.forEach { ComicTestHelper.createDummyJpeg(it, workDir) }
            repository.addFiles(filenames)
            comicStorage.all.last().let {
                it.author = "著者$comicIndex"
                it.title = "作品$comicIndex"
            }
        }
        val expected = comicStorage.all.map { it.title to it.files }
        structureStore.save()
        comicStorage.clear()

        structureStore.load()

        assertEquals(comicCount, comicStorage.all.size)
        assertEquals(expected, comicStorage.all.map { it.title to it.files })
    }

    @Test
    fun `壊れた構造ファイルはloadがfalseになりbrokenへ退避される`() {
        setting.structureFile.writeText("{ broken json ")

        assertFalse(structureStore.load())

        assertFalse(setting.structureFile.exists(), "壊れたファイルは元の場所に残らないはず")
        assertTrue(File("${setting.structureFile.path}.broken").exists(), ".broken へ退避されているはず")
        assertEquals(0, comicStorage.all.size)
    }

    @Test
    fun `旧Properties形式の構造ファイルを読み込んでJSON形式に移行する`() {
        val coverF = "coverF_000.jpg"
        val page = "page_000.jpg"
        ComicTestHelper.createDummyJpeg(coverF, workDir)
        ComicTestHelper.createDummyJpeg(page, workDir)

        val props = java.util.Properties()
        val id = "legacy-id-1"
        props.setProperty("_$id", listOf("0", "旧著者", "旧タイトル").joinToString("\t"))
        props.setProperty(coverF, id)
        props.setProperty(page, id)
        setting.legacyStructureFile.outputStream().use { props.store(it, null) }

        val loaded = structureStore.load()

        assertTrue(loaded)
        val comic = comicStorage.all.first()
        assertEquals("旧著者", comic.author)
        assertEquals("旧タイトル", comic.title)
        assertTrue(comic.files.contains(coverF))
        assertTrue(comic.files.contains(page))
        assertTrue(setting.structureFile.exists(), "JSON形式ファイルが作られているはず")
        assertFalse(setting.legacyStructureFile.exists(), "旧ファイルは残っていないはず")
        assertTrue(File("${setting.legacyStructureFile.path}.bak").exists(), ".bak にリネームされているはず")
    }
}
