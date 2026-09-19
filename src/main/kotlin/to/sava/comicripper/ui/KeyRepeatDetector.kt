package to.sava.comicripper.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType

/**
 * キーリピートを見分ける。押したままのキーから続けて届く KeyDown をリピートとみなす。
 *
 * AWT のキーイベントはリピートかどうかを持たないため、KeyDown と KeyUp の対応から判断する。
 */
internal class KeyRepeatDetector {
    private val pressedKeys = mutableSetOf<Key>()

    /**
     * キーイベントを受けて押下状態を更新し、それがリピートなら true を返す。
     * 離したことを取りこぼすと次の押下をリピートと誤るため、処理するかどうかに関わらず全イベントを渡すこと。
     */
    fun onKeyEvent(type: KeyEventType, key: Key): Boolean = when (type) {
        KeyEventType.KeyDown -> !pressedKeys.add(key)
        KeyEventType.KeyUp -> {
            pressedKeys.remove(key)
            false
        }
        else -> false
    }

    /** 押下状態を捨てる。ウィンドウがフォーカスを失い、キーを離したことを受け取れなくなるときに呼ぶ。 */
    fun reset() {
        pressedKeys.clear()
    }
}
