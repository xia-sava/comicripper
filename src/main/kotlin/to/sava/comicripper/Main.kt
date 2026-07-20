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
import to.sava.comicripper.domain.model.Comic
import to.sava.comicripper.domain.service.FileWatcher
import to.sava.comicripper.infrastructure.repository.ComicRepository
import to.sava.comicripper.model.Setting
import to.sava.comicripper.ui.ComposeWindowHost
import to.sava.comicripper.ui.main.MainWindow
import java.util.concurrent.CountDownLatch
import kotlin.system.exitProcess

private val logger = KotlinLogging.logger {}

const val VERSION = "0.8.1"

/** プロセスの生存を握るラッチ。メインウィンドウのクローズかホスト終了で解放される。 */
private val shutdownRequested = CountDownLatch(1)

fun main() {
    startKoin { modules(applicationModule) }
    val repos: ComicRepository = get(ComicRepository::class.java)
    val fileWatcher: FileWatcher = get(FileWatcher::class.java)
    val setting: Setting = get(Setting::class.java)
    val appScope: ApplicationScope = get(ApplicationScope::class.java)
    setting.load()
    Comic.workDirectoryProvider = { setting.workDirectory }

    // application {} の終了（正常・異常問わず）を生存管理へ直結させ，
    // Compose 側の未捕捉例外時にプロセスがゾンビ化しないようにする。
    ComposeWindowHost.start(onTerminated = { shutdownRequested.countDown() })
    ComposeWindowHost.show(key = "main") { onCloseRequest ->
        MainWindow(onCloseRequest = {
            onCloseRequest()
            shutdownRequested.countDown()
        })
    }

    repos.loadStructure()
    repos.reScanFiles()

    val autosaveJob = appScope.launch {
        while (true) {
            delay(30_000)
            runCatching {
                setting.save()
                repos.saveStructure()
            }.onFailure { logger.warn(it) { "autosave failed" } }
        }
    }

    fileWatcher.start(
        setting.workDirectory,
        onFilesAdded = { filenames -> repos.addFiles(filenames) },
        onFilesDeleted = { filenames -> repos.removeFiles(filenames) },
    )

    shutdownRequested.await()

    fileWatcher.stop()
    // 保存中のキャンセルによる二重書き込みを避けるため join してから最終保存する。
    runBlocking { autosaveJob.cancelAndJoin() }
    setting.save()
    repos.saveStructure()
    stopKoin()
    exitProcess(0)
}
