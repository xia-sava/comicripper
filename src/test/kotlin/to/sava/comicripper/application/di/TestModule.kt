package to.sava.comicripper.application.di

import org.koin.dsl.module
import to.sava.comicripper.domain.service.FileWatcher
import to.sava.comicripper.infrastructure.repository.ComicRepository
import to.sava.comicripper.infrastructure.service.TestFileWatcher

val testModule = module {
    // テスト用ファイル監視
    single<FileWatcher> { TestFileWatcher() }
    
    // リポジトリ層（現在は実際の実装を使用）
    single { ComicRepository() }
}