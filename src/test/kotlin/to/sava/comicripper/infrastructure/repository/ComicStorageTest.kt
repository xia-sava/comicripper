package to.sava.comicripper.infrastructure.repository

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import to.sava.comicripper.domain.model.Comic

class ComicStorageTest {

    @BeforeEach
    fun setup() {
        ComicTestHelper.disableImageLoaders()
        ComicStorage.clear()
    }

    @AfterEach
    fun tearDown() {
        ComicTestHelper.resetImageLoaders()
        ComicStorage.clear()
    }

    @Test
    fun `add後にallに含まれる`() {
        val comic = Comic()
        ComicStorage.add(comic)

        assertTrue(ComicStorage.all.contains(comic))
    }

    @Test
    fun `remove後にallから消える`() {
        val comic = Comic()
        ComicStorage.add(comic)
        ComicStorage.remove(comic)

        assertFalse(ComicStorage.all.contains(comic))
    }

    @Test
    fun `removeEmptyでファイルなしComicが除去される`() {
        val empty = Comic()
        val withFile = Comic("coverF_000.jpg")
        ComicStorage.add(empty, withFile)

        ComicStorage.removeEmpty()

        assertFalse(ComicStorage.all.contains(empty))
        assertTrue(ComicStorage.all.contains(withFile))
    }

    @Test
    fun `clearで全Comicとtargetが消える`() {
        val comic = Comic("coverF_000.jpg")
        ComicStorage.add(comic)
        ComicStorage.targetId = comic.id

        ComicStorage.clear()

        assertTrue(ComicStorage.all.isEmpty())
        assertNull(ComicStorage.targetId)
    }

    @Test
    fun `filesが全Comicのファイルを集約する`() {
        val comic1 = Comic("coverF_000.jpg")
        val comic2 = Comic("page_000.jpg")
        ComicStorage.add(comic1, comic2)

        val files = ComicStorage.files
        assertTrue(files.contains("coverF_000.jpg"))
        assertTrue(files.contains("page_000.jpg"))
    }

    @Test
    fun `targetIdを設定するとtargetが返る`() {
        val comic = Comic("coverF_000.jpg")
        ComicStorage.add(comic)
        ComicStorage.targetId = comic.id

        assertSame(comic, ComicStorage.target)
    }

    @Test
    fun `getでComic取得`() {
        val comic = Comic("coverF_000.jpg")
        ComicStorage.add(comic)

        assertSame(comic, ComicStorage[comic.id])
    }

    @Test
    fun `get存在しないidでnull`() {
        assertNull(ComicStorage["nonexistent-id"])
    }

    @Test
    fun `add後にpropertyリストにも反映される`() {
        val comic = Comic("coverF_000.jpg")
        ComicStorage.add(comic)

        assertTrue(ComicStorage.property.contains(comic))
    }

    @Test
    fun `remove後にpropertyリストからも消える`() {
        val comic = Comic("coverF_000.jpg")
        ComicStorage.add(comic)
        ComicStorage.remove(comic)

        assertFalse(ComicStorage.property.contains(comic))
    }
}
