package to.sava.comicripper.application

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * ウィンドウを閉じた後も完走させたい処理（ファイルI/O・OCR・ZIP作成・epub展開など）を
 * 実行するための、アプリ全体で共有するスコープ。Koinの`single`として登録し全画面で共有する。
 */
class ApplicationScope : CoroutineScope by CoroutineScope(SupervisorJob() + Dispatchers.IO)
