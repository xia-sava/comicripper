package to.sava.comicripper.application.di

import org.koin.dsl.module
import to.sava.comicripper.domain.service.FileWatcher
import to.sava.comicripper.infrastructure.repository.ComicRepository
import to.sava.comicripper.infrastructure.repository.ComicStorage
import to.sava.comicripper.infrastructure.service.TestFileWatcher
import to.sava.comicripper.model.Setting

val testModule = module {
    // テスト用ファイル監視
    single<FileWatcher> { TestFileWatcher() }

    // 設定・インメモリストレージ
    single { Setting() }
    single { ComicStorage() }

    // リポジトリ層（現在は実際の実装を使用）
    single { ComicRepository(get(), get()) }
}
