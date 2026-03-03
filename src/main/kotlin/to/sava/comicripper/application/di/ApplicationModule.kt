package to.sava.comicripper.application.di

import org.koin.dsl.module
import to.sava.comicripper.domain.service.FileWatcher
import to.sava.comicripper.infrastructure.repository.ComicRepository
import to.sava.comicripper.infrastructure.service.JNotifyFileWatcher

val applicationModule = module {
    // ファイル監視
    single<FileWatcher> { JNotifyFileWatcher() }

    // リポジトリ層
    single { ComicRepository() }
}