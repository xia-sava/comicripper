package to.sava.comicripper.repository

// 後方互換性のための一時的なファサード
// 既存のコードが to.sava.comicripper.repository.ComicStorage を参照し続けられるようにする
typealias ComicStorage = to.sava.comicripper.infrastructure.repository.ComicStorage