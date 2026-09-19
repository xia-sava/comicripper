package to.sava.comicripper.ui.detail

import androidx.compose.ui.input.key.Key
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

/**
 * 押したキーと Ctrl の有無。
 * [Key] は value class なので、テストの引数に直接取ると JVM 上の型が合わず JUnit から渡せない。
 */
internal class KeyStroke(private val label: String, val key: Key, val isCtrlPressed: Boolean = false) {
    override fun toString() = label
}

internal class DetailWindowTest {

    @Nested
    inner class `detailKeyAction` {

        @ParameterizedTest(name = "{0}")
        @MethodSource("to.sava.comicripper.ui.detail.DetailWindowTest#keyActions")
        fun `入力欄の外では各キーに処理を割り当てる`(stroke: KeyStroke, expected: DetailKeyAction) {
            assertEquals(expected, detailKeyAction(stroke.key, stroke.isCtrlPressed, isEditingText = false))
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource("to.sava.comicripper.ui.detail.DetailWindowTest#textEditingKeys")
        fun `入力欄の編集中はカーソル移動と全選択のキーを入力欄へ譲る`(stroke: KeyStroke) {
            assertNull(detailKeyAction(stroke.key, stroke.isCtrlPressed, isEditingText = true))
        }

        @Test
        fun `入力欄の編集中でも削除は効く`() {
            assertEquals(DetailKeyAction.DeleteImage, detailKeyAction(Key.D, isCtrlPressed = true, isEditingText = true))
        }

        @Test
        fun `入力欄の編集中のEscは画面を閉じずに入力欄から抜ける`() {
            assertEquals(
                DetailKeyAction.LeaveTextField,
                detailKeyAction(Key.Escape, isCtrlPressed = false, isEditingText = true),
            )
        }

        @Test
        fun `Ctrlを伴わない文字キーには何も割り当てない`() {
            // 入力欄の外で押した文字キーを、Ctrl 付きの処理と取り違えない。
            assertNull(detailKeyAction(Key.D, isCtrlPressed = false, isEditingText = false))
        }

        @Test
        fun `割り当てていないキーには何もしない`() {
            assertNull(detailKeyAction(Key.Q, isCtrlPressed = true, isEditingText = false))
        }
    }

    companion object {
        @JvmStatic
        fun keyActions(): List<Arguments> = listOf(
            Arguments.of(KeyStroke("←", Key.DirectionLeft), DetailKeyAction.PreviousPage),
            Arguments.of(KeyStroke("→", Key.DirectionRight), DetailKeyAction.NextPage),
            Arguments.of(KeyStroke("Home", Key.MoveHome), DetailKeyAction.FirstPage),
            Arguments.of(KeyStroke("Ctrl+A", Key.A, isCtrlPressed = true), DetailKeyAction.FirstPage),
            Arguments.of(KeyStroke("End", Key.MoveEnd), DetailKeyAction.LastPage),
            Arguments.of(KeyStroke("Ctrl+E", Key.E, isCtrlPressed = true), DetailKeyAction.LastPage),
            Arguments.of(KeyStroke("Ctrl+D", Key.D, isCtrlPressed = true), DetailKeyAction.DeleteImage),
            Arguments.of(KeyStroke("Ctrl+L", Key.L, isCtrlPressed = true), DetailKeyAction.ReleaseImage),
            Arguments.of(KeyStroke("F5", Key.F5), DetailKeyAction.ReloadImages),
            Arguments.of(KeyStroke("Ctrl+O", Key.O, isCtrlPressed = true), DetailKeyAction.Ocr),
            Arguments.of(KeyStroke("Ctrl+T", Key.T, isCtrlPressed = true), DetailKeyAction.CutCover),
            Arguments.of(KeyStroke("F2", Key.F2), DetailKeyAction.FocusAuthor),
            Arguments.of(KeyStroke("Ctrl+I", Key.I, isCtrlPressed = true), DetailKeyAction.FocusIsbn),
            Arguments.of(KeyStroke("Esc", Key.Escape), DetailKeyAction.Close),
        )

        @JvmStatic
        fun textEditingKeys(): List<KeyStroke> = listOf(
            KeyStroke("←", Key.DirectionLeft),
            KeyStroke("→", Key.DirectionRight),
            KeyStroke("Ctrl+←", Key.DirectionLeft, isCtrlPressed = true),
            KeyStroke("Home", Key.MoveHome),
            KeyStroke("End", Key.MoveEnd),
            KeyStroke("Ctrl+A", Key.A, isCtrlPressed = true),
            KeyStroke("Ctrl+E", Key.E, isCtrlPressed = true),
        )
    }
}
