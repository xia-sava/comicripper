package to.sava.comicripper.infrastructure.service

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import to.sava.comicripper.domain.model.Comic
import to.sava.comicripper.domain.service.FileWatcher
import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchKey
import java.nio.file.WatchService

private val logger = KotlinLogging.logger {}

/**
 * java.nio.file.WatchService（JDK標準）によるファイル監視実装。
 * 最初のイベントを受けてから200msのバッチウィンドウを設け、その間に届いた追加イベントも
 * まとめて1回のコールバックにする。
 */
class NioFileWatcher : FileWatcher, CoroutineScope {
    private val job = Job()
    override val coroutineContext get() = Dispatchers.IO + job

    private var isRunning = false
    private var watchService: WatchService? = null

    override fun start(
        workDirectory: String,
        onFilesAdded: (filenames: List<String>) -> Unit,
        onFilesDeleted: (filenames: List<String>) -> Unit
    ) {
        if (isRunning) return
        isRunning = true

        val service = FileSystems.getDefault().newWatchService()
        watchService = service
        Path.of(workDirectory).register(
            service,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_DELETE,
        )

        launch {
            val createdFiles = mutableListOf<String>()
            val deletedFiles = mutableListOf<String>()
            while (isRunning) {
                // 最初のイベントが届くまでブロッキング待機する。
                val firstKey = try {
                    service.take()
                } catch (_: ClosedWatchServiceException) {
                    break
                } catch (_: InterruptedException) {
                    break
                }
                collectEvents(firstKey, createdFiles, deletedFiles)
                firstKey.reset()

                // バッチウィンドウ中に追加で届いたイベントも集める。
                delay(200)
                while (isRunning) {
                    val key = service.poll() ?: break
                    collectEvents(key, createdFiles, deletedFiles)
                    key.reset()
                }

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
    }

    private fun collectEvents(
        key: WatchKey,
        createdFiles: MutableList<String>,
        deletedFiles: MutableList<String>,
    ) {
        for (event in key.pollEvents()) {
            val kind = event.kind()
            if (kind == StandardWatchEventKinds.OVERFLOW) {
                logger.warn { "watch event overflow, some file changes may have been missed" }
                continue
            }
            @Suppress("UNCHECKED_CAST")
            val filename = (event as java.nio.file.WatchEvent<Path>).context().toString()
            if (!filename.matches(Comic.TARGET_REGEX)) {
                continue
            }
            when (kind) {
                StandardWatchEventKinds.ENTRY_CREATE -> createdFiles.add(filename)
                StandardWatchEventKinds.ENTRY_DELETE -> deletedFiles.add(filename)
            }
        }
    }

    override fun stop() {
        isRunning = false
        // take() でブロック中のスレッドはコルーチンキャンセルを認識できないため、
        // watchService を閉じて ClosedWatchServiceException で確実に抜けさせる。
        runCatching { watchService?.close() }
        job.cancel()
    }
}
