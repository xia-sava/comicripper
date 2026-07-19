package to.sava.comicripper.ui.main

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect

/**
 * カードのドラッグ&ドロップによる Comic マージのための状態。
 *
 * 枠色表示に使う [draggingId]（ドラッグ元）と [dropTargetId]（ドロップ先候補）だけを
 * 再コンポーズ対象の状態として持つ。座標マップは EDT からのみ触られるため素の Map で保持する。
 *
 * 座標は 2 種類を別々に持つ:
 * - [origins]: クリップ前の原点（`positionInWindow()`）。ドラッグ中のポインタ絶対位置を
 *   毎イベント再計算するのに使う。クリップ済み bounds 単体ではスクロールで原点がずれるため。
 * - [bounds]: クリップ済み bounds（`boundsInWindow()`）。ヒットテスト対象。
 *   画面外へスクロールしたカードは矩形が縮む/消えるので自然にドロップ対象から外れる。
 */
class ComicDragState {
    var draggingId by mutableStateOf<String?>(null)
        private set
    var dropTargetId by mutableStateOf<String?>(null)
        private set

    private val origins = mutableMapOf<String, Offset>()
    private val bounds = mutableMapOf<String, Rect>()

    /** カードの原点（クリップ前）とクリップ済み bounds を登録する。 */
    fun register(id: String, origin: Offset, clippedBounds: Rect) {
        origins[id] = origin
        bounds[id] = clippedBounds
    }

    /**
     * 破棄されたカードのエントリを除去する（stale bounds による誤ヒット防止）。
     * 破棄カードがドラッグ状態に残らないよう、該当していれば状態もクリアする。
     */
    fun unregister(id: String) {
        origins.remove(id)
        bounds.remove(id)
        if (draggingId == id) {
            draggingId = null
        }
        if (dropTargetId == id) {
            dropTargetId = null
        }
    }

    /** ドラッグ開始。 */
    fun start(id: String) {
        draggingId = id
        dropTargetId = null
    }

    /**
     * ドラッグ中の更新。[localPosition] はドラッグ元カードのローカル座標
     * （`change.position`）。ポインタのウィンドウ座標を
     * 「クリップ前原点 + ローカル座標」で毎イベント再計算し、ドロップ先を更新する
     * （ローカル delta 累積だと、ドラッグ中のスクロールや再フローでカードが動いた際に
     * ポインタ位置が恒久的にずれるため）。
     */
    fun drag(localPosition: Offset) {
        val id = draggingId ?: return
        val origin = origins[id] ?: return
        val pointerInWindow = origin + localPosition
        dropTargetId = bounds.entries
            .firstOrNull { (targetId, rect) -> targetId != id && rect.contains(pointerInWindow) }
            ?.key
    }

    /**
     * ドラッグ終了。マージすべき (src, dst) を返し、状態をクリアする。
     * ドロップ先が無ければ null。
     */
    fun end(): Pair<String, String>? {
        val src = draggingId
        val dst = dropTargetId
        draggingId = null
        dropTargetId = null
        return if (src != null && dst != null && src != dst) src to dst else null
    }

    /** ドラッグ中止。状態のみクリアする。 */
    fun cancel() {
        draggingId = null
        dropTargetId = null
    }
}
