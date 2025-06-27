package to.sava.comicripper.infrastructure.service

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.AfterEach
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import to.sava.comicripper.application.di.testModule
import to.sava.comicripper.domain.service.FileWatcher
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

class FileWatcherTest : KoinComponent {
    
    private val fileWatcher: FileWatcher by inject()
    
    @BeforeEach
    fun setup() {
        startKoin {
            modules(testModule)
        }
    }
    
    @AfterEach
    fun tearDown() {
        stopKoin()
    }
    
    @Test
    fun `FileWatcherがテスト実装を返すことを確認`() {
        assertTrue(fileWatcher is TestFileWatcher)
    }
    
    @Test
    fun `ファイル追加のシミュレーションが正常に動作する`() {
        val testFileWatcher = fileWatcher as TestFileWatcher
        val addedFiles = mutableListOf<String>()
        
        testFileWatcher.start(
            workDirectory = "/test",
            onFilesAdded = { filenames -> addedFiles.addAll(filenames) },
            onFilesDeleted = { }
        )
        
        testFileWatcher.simulateFileAdded("coverF001.jpg")
        testFileWatcher.simulateFileAdded("page001.jpg")
        
        assertEquals(2, addedFiles.size)
        assertEquals("coverF001.jpg", addedFiles[0])
        assertEquals("page001.jpg", addedFiles[1])
    }
    
    @Test
    fun `複数ファイルの同時追加をテストできる`() {
        val testFileWatcher = fileWatcher as TestFileWatcher
        val addedFiles = mutableListOf<List<String>>()
        
        testFileWatcher.start(
            workDirectory = "/test",
            onFilesAdded = { filenames -> addedFiles.add(filenames) },
            onFilesDeleted = { }
        )
        
        // 複数ファイルの同時追加をシミュレート
        val batchFiles = listOf("page001.jpg", "page002.jpg", "page003.jpg")
        testFileWatcher.simulateFilesAdded(batchFiles)
        
        assertEquals(1, addedFiles.size)
        assertEquals(3, addedFiles[0].size)
        assertEquals(batchFiles, addedFiles[0])
    }
}