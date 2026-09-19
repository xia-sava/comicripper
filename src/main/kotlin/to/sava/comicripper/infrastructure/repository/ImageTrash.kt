package to.sava.comicripper.infrastructure.repository

import io.github.oshai.kotlinlogging.KotlinLogging
import to.sava.comicripper.domain.model.Comic
import to.sava.comicripper.model.Setting
import java.awt.Desktop
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.UUID

private val logger = KotlinLogging.logger {}

/** 削除の取り消しの結果。 */
sealed interface UndoResult {
    /** [filename] を [comic] へ戻した。 */
    data class Restored(val comic: Comic, val filename: String) : UndoResult

    /** 取り消す削除が無い。 */
    data object NothingToUndo : UndoResult

    /** [filename] を戻せなかった。退避したファイルは退避先に残る。 */
    data class Failed(val filename: String) : UndoResult
}

/** OS のごみ箱へ送る。ごみ箱を扱えない環境では送らずに false を返す。 */
private fun moveToDesktopTrash(file: File): Boolean =
    Desktop.isDesktopSupported() &&
        Desktop.getDesktop().isSupported(Desktop.Action.MOVE_TO_TRASH) &&
        Desktop.getDesktop().moveToTrash(file)

/**
 * 画像の削除を取り消せるようにする。
 *
 * 削除した画像は退避先（[Setting.trashDirectory]）へ移し、新しいものから順に戻せるよう履歴を持つ。
 * 履歴はメモリにだけ持つ。退避した画像は起動時と終了時に [purge] / [purgeAll] で OS のごみ箱へ送り、
 * アプリを閉じた後でもエクスプローラから戻せるようにする。
 *
 * 削除と取り消しは画面から、片付けは起動時・終了時に別のスレッドから呼ばれる。
 * 片付けが取り消せる画像を送らないよう、ファイルの移動と履歴の更新はロックの下でまとめて行なう。
 *
 * @param moveToOsTrash ファイルを OS のごみ箱へ送る処理。送れなければ false を返す。テストから差し替える。
 */
class ImageTrash(
    private val setting: Setting,
    private val comicStorage: ComicStorage,
    private val repository: ComicRepository,
    private val moveToOsTrash: (File) -> Boolean = ::moveToDesktopTrash,
) {
    /**
     * 削除1件。退避先は削除ごとに別のフォルダにして、同じ名前の画像を何度消しても重ならないようにする
     * （ファイル名は元のまま残し、退避先を見れば元の画像が分かるようにする）。
     */
    private class Entry(val comic: Comic, val filename: String, val original: File, val trashed: File)

    private val history = ArrayDeque<Entry>()

    /**
     * [comic] の画像 [filename] を退避して構成から外す。退避できなければ false を返す。
     */
    fun delete(comic: Comic, filename: String): Boolean {
        val original = File(setting.workDirectory, filename)
        val trashed = File(File(setting.trashDirectory, UUID.randomUUID().toString()), filename)
        synchronized(history) {
            try {
                Files.createDirectories(trashed.parentFile.toPath())
                Files.move(original.toPath(), trashed.toPath())
            } catch (e: IOException) {
                logger.warn(e) { "move to trash directory failed: $filename" }
                return false
            }
            history.addLast(Entry(comic, filename, original, trashed))
        }
        // ファイル監視が動いていない環境でも表示を合わせるため、自分でも構成から外す。
        repository.removeFiles(listOf(filename))
        return true
    }

    /**
     * 最後に削除した画像を元の場所へ戻し、コミックへ入れ直す。
     *
     * 戻す先は元のコミック。一覧から外れていても、画像が1枚も残っていない（最後の1枚を消した）なら
     * 作者名・題名ごと一覧へ戻す。画像を残したまま外れた（ZIP 作成を終えた）なら、そこへは戻さず
     * その画像だけのコミックにする。
     * 元の場所に同じ名前のファイルができていれば上書きせず、戻せなかったとする。
     */
    fun undo(): UndoResult = synchronized(history) {
        val entry = history.removeLastOrNull() ?: return UndoResult.NothingToUndo
        if (entry.original.exists() || !entry.trashed.exists()) {
            logger.warn { "cannot restore from trash directory: ${entry.filename}" }
            return UndoResult.Failed(entry.filename)
        }
        // 先に構成へ入れておき、戻したファイルの追加通知を取り込み済みとして無視させる。
        val target = register(entry.comic, entry.filename)
        try {
            Files.move(entry.trashed.toPath(), entry.original.toPath())
        } catch (e: IOException) {
            logger.warn(e) { "restore from trash directory failed: ${entry.filename}" }
            repository.removeFiles(listOf(entry.filename))
            return UndoResult.Failed(entry.filename)
        }
        entry.trashed.parentFile.delete()
        UndoResult.Restored(target, entry.filename)
    }

    /** [filename] を戻すコミックを決めて入れ、そのコミックを返す。 */
    private fun register(comic: Comic, filename: String): Comic {
        if (comic in comicStorage.all) {
            addTo(comic, filename)
            return comic
        }
        val target = if (comic.files.isEmpty()) comic.also { addTo(it, filename) } else Comic(filename)
        comicStorage.add(target)
        return target
    }

    private fun addTo(comic: Comic, filename: String) {
        // 表紙は種類ごとに1枚なので、削除の後に同じ種類の表紙ができていれば、そちらを別のコミックへ出す。
        comic.addFile(filename)?.let { comicStorage.add(Comic(it)) }
    }

    /**
     * 退避した画像のうち、取り消しの履歴に無いもの（前回までに退避したもの）を OS のごみ箱へ送る。
     * 送れなかった画像は退避先に残し、次の片付けで改めて送る。
     */
    fun purge() {
        val directories = synchronized(history) {
            val undoable = history.map { it.trashed.parentFile.name }.toSet()
            setting.trashDirectory.listFiles { file -> file.isDirectory && file.name !in undoable }.orEmpty()
        }
        directories.forEach { directory ->
            directory.listFiles().orEmpty().forEach(::sendToOsTrash)
            // 送り残しがあれば空にならず、消えずに残る。
            directory.delete()
        }
        setting.trashDirectory.delete()
    }

    /** 取り消しの履歴を捨てて、退避した画像をすべて OS のごみ箱へ送る。終了時に呼ぶ。 */
    fun purgeAll() {
        synchronized(history) { history.clear() }
        purge()
    }

    private fun sendToOsTrash(file: File) {
        try {
            if (!moveToOsTrash(file)) {
                logger.warn { "OS trash unavailable, kept in trash directory: $file" }
            }
        } catch (e: Exception) {
            logger.warn(e) { "move to OS trash failed: $file" }
        }
    }
}
