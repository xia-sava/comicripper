package to.sava.comicripper.infrastructure.service

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/**
 * java.nio.file.WatchService は実ファイルシステムイベントに依存するため、
 * 実際に一時ディレクトリへファイルを作成/削除して検証する統合テスト。
 */
class NioFileWatcherTest {

    private val watcher = NioFileWatcher()

    @AfterEach
    fun tearDown() {
        watcher.stop()
    }

    @Test
    fun `対象拡張子のファイル作成で onFilesAdded が呼ばれる`(@TempDir tempDir: Path) = runBlocking {
        val added = mutableListOf<String>()
        watcher.start(
            workDirectory = tempDir.toString(),
            onFilesAdded = { added.addAll(it) },
            onFilesDeleted = {},
        )
        delay(300)

        Files.createFile(tempDir.resolve("coverF_001.jpg"))

        withTimeout(5_000) {
            while (added.isEmpty()) {
                delay(50)
            }
        }
        assertEquals(listOf("coverF_001.jpg"), added)
    }

    @Test
    fun `対象拡張子でないファイルは通知されない`(@TempDir tempDir: Path) = runBlocking {
        val added = mutableListOf<String>()
        watcher.start(
            workDirectory = tempDir.toString(),
            onFilesAdded = { added.addAll(it) },
            onFilesDeleted = {},
        )
        delay(300)

        Files.createFile(tempDir.resolve("readme.txt"))
        // 対象ファイルも1つ作り、その通知が来ることで監視自体は機能していることを確認する。
        Files.createFile(tempDir.resolve("page_001.jpg"))

        withTimeout(5_000) {
            while (added.isEmpty()) {
                delay(50)
            }
        }
        assertEquals(listOf("page_001.jpg"), added)
    }

    @Test
    fun `ファイル削除で onFilesDeleted が呼ばれる`(@TempDir tempDir: Path) = runBlocking {
        val target = tempDir.resolve("page_001.jpg")
        Files.createFile(target)

        val deleted = mutableListOf<String>()
        watcher.start(
            workDirectory = tempDir.toString(),
            onFilesAdded = {},
            onFilesDeleted = { deleted.addAll(it) },
        )
        delay(300)

        Files.delete(target)

        withTimeout(5_000) {
            while (deleted.isEmpty()) {
                delay(50)
            }
        }
        assertEquals(listOf("page_001.jpg"), deleted)
    }

    @Test
    fun `stop() 後は新しいイベントを通知しない`(@TempDir tempDir: Path) = runBlocking {
        val added = mutableListOf<String>()
        watcher.start(
            workDirectory = tempDir.toString(),
            onFilesAdded = { added.addAll(it) },
            onFilesDeleted = {},
        )
        delay(300)
        watcher.stop()
        delay(200)

        Files.createFile(tempDir.resolve("coverF_002.jpg"))
        delay(500)

        assertTrue(added.isEmpty())
    }
}
