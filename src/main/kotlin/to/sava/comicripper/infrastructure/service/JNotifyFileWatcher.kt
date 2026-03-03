package to.sava.comicripper.infrastructure.service

import kotlinx.coroutines.*
import net.contentobjects.jnotify.win32.JNotify_win32
import to.sava.comicripper.domain.model.Comic
import to.sava.comicripper.domain.service.FileWatcher

class JNotifyFileWatcher : FileWatcher, CoroutineScope {
    private val job = Job()
    override val coroutineContext get() = Dispatchers.Default + job

    private val fileCreatedQueue = mutableListOf<String>()
    private val fileDeletedQueue = mutableListOf<String>()

    private var isRunning = false

    override fun start(
        workDirectory: String,
        onFilesAdded: (filenames: List<String>) -> Unit,
        onFilesDeleted: (filenames: List<String>) -> Unit
    ) {
        if (isRunning) return
        isRunning = true

        try {
            JNotify_win32.addWatch(
                workDirectory,
                JNotify_win32.FILE_ACTION_ADDED.toLong() or JNotify_win32.FILE_ACTION_REMOVED.toLong(),
                false
            )
            JNotify_win32.setNotifyListener { _, action, _, filePath ->
                when (action) {
                    JNotify_win32.FILE_ACTION_ADDED -> {
                        if (filePath.matches(Comic.TARGET_REGEX)) {
                            synchronized(fileCreatedQueue) {
                                fileCreatedQueue.add(filePath)
                            }
                        }
                    }

                    JNotify_win32.FILE_ACTION_REMOVED -> {
                        if (filePath.matches(Comic.TARGET_REGEX)) {
                            synchronized(fileDeletedQueue) {
                                fileDeletedQueue.add(filePath)
                            }
                        }
                    }
                }
            }

            launch {
                val createdFiles = mutableListOf<String>()
                val deletedFiles = mutableListOf<String>()
                while (isRunning) {
                    synchronized(fileCreatedQueue) {
                        if (fileCreatedQueue.isNotEmpty()) {
                            createdFiles.addAll(fileCreatedQueue)
                            fileCreatedQueue.clear()
                        }
                    }
                    synchronized(fileDeletedQueue) {
                        if (fileDeletedQueue.isNotEmpty()) {
                            deletedFiles.addAll(fileDeletedQueue)
                            fileDeletedQueue.clear()
                        }
                    }
                    delay(200)
                    if (createdFiles.isNotEmpty()) {
                        onFilesAdded(createdFiles.toList())
                        createdFiles.clear()
                    }
                    if (deletedFiles.isNotEmpty()) {
                        onFilesDeleted(deletedFiles.toList())
                        deletedFiles.clear()
                    }
                }
            }
        } catch (_: UnsatisfiedLinkError) {
            // JNotify が正常にインストールされてない気がするけど
            // ファイル見張らないモードで一応起動する．
            isRunning = false
        }
    }

    override fun stop() {
        isRunning = false
        job.cancel()
    }
}