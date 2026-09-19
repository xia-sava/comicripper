package to.sava.comicripper.ui

import androidx.compose.ui.input.key.Key

/**
 * パラメタライズテストに渡す、押したキーと Ctrl の有無。[label] はテストの表示名に使う。
 * [Key] は value class なので、テストの引数に直接取ると JVM 上の型が合わず JUnit から渡せない。
 */
internal class KeyStroke(private val label: String, val key: Key, val isCtrlPressed: Boolean = false) {
    override fun toString() = label
}
