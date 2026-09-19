package to.sava.comicripper

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.java.KoinJavaComponent.get
import to.sava.comicripper.application.ApplicationScope
import to.sava.comicripper.application.di.applicationModule
import to.sava.comicripper.domain.service.FileWatcher
import to.sava.comicripper.infrastructure.repository.ComicRepository
import to.sava.comicripper.infrastructure.repository.ImageTrash
import to.sava.comicripper.infrastructure.repository.StructureStore
import to.sava.comicripper.model.Setting
import to.sava.comicripper.ui.ComposeWindowHost
import to.sava.comicripper.ui.main.MainWindow
import java.util.concurrent.CountDownLatch
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

const val VERSION = "1.1.0"

/** プロセスの生存を握るラッチ。メインウィンドウのクローズかホスト終了で解放される。 */
private val shutdownRequested = CountDownLatch(1)

fun main() {
    startKoin { modules(applicationModule) }
    val repos: ComicRepository = get(ComicRepository::class.java)
    val structureStore: StructureStore = get(StructureStore::class.java)
    val imageTrash: ImageTrash = get(ImageTrash::class.java)
    val fileWatcher: FileWatcher = get(FileWatcher::class.java)
    val setting: Setting = get(Setting::class.java)
    val appScope: ApplicationScope = get(ApplicationScope::class.java)
    setting.load()
    setting.fixStructureDirectory()

    // application {} の終了（正常・異常問わず）を生存管理へ直結させ，
    // Compose 側の未捕捉例外時にプロセスがゾンビ化しないようにする。
    ComposeWindowHost.start(onTerminated = { shutdownRequested.countDown() })
    ComposeWindowHost.show(key = "main") { onCloseRequest ->
        MainWindow(onCloseRequest = {
            onCloseRequest()
            shutdownRequested.countDown()
        })
    }

    structureStore.load()
    repos.reScanFiles()

    // 前回までに退避した画像を片付ける。ごみ箱へ送るのは1件ずつ時間がかかるため、起動を待たせない。
    appScope.launch { imageTrash.purge() }

    val autosaveJob = appScope.launch {
        while (true) {
            delay(30_000)
            runCatching {
                setting.save()
                structureStore.save()
            }.onFailure { logger.warn(it) { "autosave failed" } }
        }
    }

    fileWatcher.start(
        setting.workDirectory,
        onFilesAdded = { filenames -> repos.addFiles(filenames) },
        onFilesDeleted = { filenames -> repos.removeDeletedFiles(filenames) },
    )

    shutdownRequested.await()

    fileWatcher.stop()
    // 保存中のキャンセルによる二重書き込みを避けるため join してから最終保存する。
    runBlocking { autosaveJob.cancelAndJoin() }
    setting.save()
    structureStore.save()
    // 取り消しの履歴は終了で消えるので、退避した画像はここで片付ける。
    imageTrash.purgeAll()
    stopKoin()
    exitProcess(0)
}
