package to.sava.comicripper.repository

// 後方互換性のための一時的なファサード
// 既存のコードが to.sava.comicripper.repository.ComicRepository を参照し続けられるようにする
typealias ComicRepository = to.sava.comicripper.infrastructure.repository.ComicRepository