package to.sava.comicripper.infrastructure.repository

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import to.sava.comicripper.domain.model.Comic

class ComicStorageTest {

    private val comicStorage = ComicStorage()

    @BeforeEach
    fun setup() {
        ComicTestHelper.disableImageLoaders()
        comicStorage.clear()
    }

    @AfterEach
    fun tearDown() {
        ComicTestHelper.resetImageLoaders()
        comicStorage.clear()
    }

    @Test
    fun `add後にallに含まれる`() {
        val comic = Comic()
        comicStorage.add(comic)

        assertTrue(comicStorage.all.contains(comic))
    }

    @Test
    fun `remove後にallから消える`() {
        val comic = Comic()
        comicStorage.add(comic)
        comicStorage.remove(comic)

        assertFalse(comicStorage.all.contains(comic))
    }

    @Test
    fun `removeEmptyでファイルなしComicが除去される`() {
        val empty = Comic()
        val withFile = Comic("coverF_000.jpg")
        comicStorage.add(empty, withFile)

        comicStorage.removeEmpty()

        assertFalse(comicStorage.all.contains(empty))
        assertTrue(comicStorage.all.contains(withFile))
    }

    @Test
    fun `clearで全Comicとtargetが消える`() {
        val comic = Comic("coverF_000.jpg")
        comicStorage.add(comic)
        comicStorage.targetId = comic.id

        comicStorage.clear()

        assertTrue(comicStorage.all.isEmpty())
        assertNull(comicStorage.targetId)
    }

    @Test
    fun `filesが全Comicのファイルを集約する`() {
        val comic1 = Comic("coverF_000.jpg")
        val comic2 = Comic("page_000.jpg")
        comicStorage.add(comic1, comic2)

        val files = comicStorage.files
        assertTrue(files.contains("coverF_000.jpg"))
        assertTrue(files.contains("page_000.jpg"))
    }

    @Test
    fun `targetIdを設定するとtargetが返る`() {
        val comic = Comic("coverF_000.jpg")
        comicStorage.add(comic)
        comicStorage.targetId = comic.id

        assertSame(comic, comicStorage.target)
    }

    @Test
    fun `getでComic取得`() {
        val comic = Comic("coverF_000.jpg")
        comicStorage.add(comic)

        assertSame(comic, comicStorage[comic.id])
    }

    @Test
    fun `get存在しないidでnull`() {
        assertNull(comicStorage["nonexistent-id"])
    }

    @Test
    fun `flowのvalueがallと一致する`() {
        val comic = Comic("coverF_000.jpg")
        comicStorage.add(comic)

        assertEquals(comicStorage.all, comicStorage.storage.value)
    }

    @Test
    fun `addでflowのvalueが更新される`() {
        val comic = Comic("coverF_000.jpg")
        comicStorage.add(comic)

        assertTrue(comicStorage.storage.value.contains(comic))
    }

    @Test
    fun `removeでflowのvalueが更新される`() {
        val comic = Comic("coverF_000.jpg")
        comicStorage.add(comic)
        comicStorage.remove(comic)

        assertFalse(comicStorage.storage.value.contains(comic))
    }

    @Test
    fun `clearでflowのvalueが空になる`() {
        comicStorage.add(Comic("coverF_000.jpg"), Comic("page_000.jpg"))
        comicStorage.clear()

        assertTrue(comicStorage.storage.value.isEmpty())
    }

    @Test
    fun `getNullIdでnull`() {
        assertNull(comicStorage[null])
    }
}
