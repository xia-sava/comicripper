package to.sava.comicripper.infrastructure.service

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import to.sava.comicripper.domain.service.FileWatcher

class TestFileWatcher : FileWatcher {
    private var onFilesAddedCallback: ((List<String>) -> Unit)? = null
    private var onFilesDeletedCallback: ((List<String>) -> Unit)? = null
    
    override fun start(
        workDirectory: String,
        onFilesAdded: (filenames: List<String>) -> Unit,
        onFilesDeleted: (filenames: List<String>) -> Unit
    ) {
        this.onFilesAddedCallback = onFilesAdded
        this.onFilesDeletedCallback = onFilesDeleted
    }
    
    override fun stop() {
        onFilesAddedCallback = null
        onFilesDeletedCallback = null
    }
    
    // テスト用のファイルイベントシミュレーションメソッド
    fun simulateFilesAdded(filenames: List<String>) {
        onFilesAddedCallback?.invoke(filenames)
    }
    
    fun simulateFileAdded(filename: String) {
        simulateFilesAdded(listOf(filename))
    }
    
    fun simulateFilesDeleted(filenames: List<String>) {
        onFilesDeletedCallback?.invoke(filenames)
    }
    
    fun simulateFileDeleted(filename: String) {
        simulateFilesDeleted(listOf(filename))
    }
}