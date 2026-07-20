package to.sava.comicripper.application.di

import org.koin.dsl.module
import to.sava.comicripper.application.ApplicationScope
import to.sava.comicripper.domain.service.FileWatcher
import to.sava.comicripper.infrastructure.repository.ComicRepository
import to.sava.comicripper.infrastructure.repository.ComicStorage
import to.sava.comicripper.infrastructure.service.NioFileWatcher
import to.sava.comicripper.model.Setting

val applicationModule = module {
    // ウィンドウを閉じても完走すべき処理の実行スコープ
    single { ApplicationScope() }

    // ファイル監視
    single<FileWatcher> { NioFileWatcher() }

    // 設定・インメモリストレージ
    single { Setting() }
    single { ComicStorage() }

    // リポジトリ層
    single { ComicRepository(get(), get()) }
}
