package to.sava.comicripper.infrastructure.repository

import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import to.sava.comicripper.domain.model.Comic

/**
 * 読み込み済みのコミックを保持する。
 *
 * 中身は Compose の snapshot state なので、[all] や [target] をコンポジションから読めば
 * そのまま変更が反映される。
 */
@Stable
class ComicStorage {
    private val _storage = mutableStateListOf<Comic>()

    /**
     * 操作対象のコミック。取り込んだファイルの振り分け先であり、一覧の選択位置でもある。
     * 画面側が別に選択位置を持つと二重管理になるため、ここを唯一の持ち主とする。
     */
    var targetId: String? by mutableStateOf(null)

    /** 読み込み順のコミック一覧。中身が変わったときだけ新しいリストになる。 */
    val all: List<Comic> by derivedStateOf(structuralEqualityPolicy()) { _storage.toList() }

    val files get() = all.flatMap { it.files }
    val target get() = this[targetId]

    fun add(vararg comics: Comic) {
        _storage.addAll(comics)
    }

    fun remove(vararg comics: Comic) {
        _storage.removeAll(comics.toSet())
    }

    fun removeEmpty() {
        _storage.removeAll { it.files.isEmpty() }
    }

    fun clear() {
        _storage.clear()
        targetId = null
    }

    operator fun get(id: String?): Comic? = id?.let { target -> all.firstOrNull { it.id == target } }
}
