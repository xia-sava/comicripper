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

    @Volatile
    private var isRunning = false
    private var watchService: WatchService? = null

    override fun start(
        workDirectory: String,
        onFilesAdded: (filenames: List<String>) -> Unit,
        onFilesDeleted: (filenames: List<String>) -> Unit
    ) {
        if (isRunning) return

        val service = try {
            openWatchService(workDirectory)
        } catch (e: Exception) {
            // 監視を開始できなくてもアプリの起動は続行する（再スキャンでの手動反映は可能）。
            logger.error(e) { "file watch setup failed, watching disabled: $workDirectory" }
            return
        }
        isRunning = true
        watchService = service

        launch {
            try {
                watchLoop(service, onFilesAdded, onFilesDeleted)
            } catch (_: ClosedWatchServiceException) {
                // stop() が watchService を close した場合の正常経路。
            }
        }
    }

    private fun openWatchService(workDirectory: String): WatchService {
        val service = FileSystems.getDefault().newWatchService()
        try {
            Path.of(workDirectory).register(
                service,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_DELETE,
            )
        } catch (e: Exception) {
            runCatching { service.close() }
            throw e
        }
        return service
    }

    private suspend fun watchLoop(
        service: WatchService,
        onFilesAdded: (filenames: List<String>) -> Unit,
        onFilesDeleted: (filenames: List<String>) -> Unit,
    ) {
        val createdFiles = mutableListOf<String>()
        val deletedFiles = mutableListOf<String>()
        while (isRunning) {
            // 最初のイベントが届くまでブロッキング待機する。
            val firstKey = try {
                service.take()
            } catch (_: InterruptedException) {
                break
            }
            var keyValid = collectEventsAndReset(firstKey, createdFiles, deletedFiles)
            if (keyValid) {
                // バッチウィンドウ中に追加で届いたイベントも集める。
                delay(200)
                while (isRunning) {
                    val key = service.poll() ?: break
                    keyValid = collectEventsAndReset(key, createdFiles, deletedFiles)
                    if (!keyValid) break
                }
            }

            if (createdFiles.isNotEmpty()) {
                onFilesAdded(createdFiles.toList())
                createdFiles.clear()
            }
            if (deletedFiles.isNotEmpty()) {
                onFilesDeleted(deletedFiles.toList())
                deletedFiles.clear()
            }

            if (!keyValid) {
                // 監視ディレクトリの削除等でキーが無効化されると以後イベントは届かない。
                logger.warn { "watch key invalidated, file watching stopped" }
                break
            }
        }
    }

    /** キーのイベントを収集して reset する。キーが無効化されていたら false を返す。 */
    private fun collectEventsAndReset(
        key: WatchKey,
        createdFiles: MutableList<String>,
        deletedFiles: MutableList<String>,
    ): Boolean {
        collectEvents(key, createdFiles, deletedFiles)
        return key.reset()
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
