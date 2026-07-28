package to.sava.comicripper.ui.main

import androidx.compose.ui.geometry.Rect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class MainWindowTest {

    @Nested
    inner class `selectionAfterMove` {

        @Test
        fun `次のコミックへ移る`() {
            assertEquals("b", selectionAfterMove(listOf("a", "b", "c"), "a", 1))
        }

        @Test
        fun `前のコミックへ移る`() {
            assertEquals("a", selectionAfterMove(listOf("a", "b", "c"), "b", -1))
        }

        @Test
        fun `末尾で次へ移ろうとしても選択は変わらない`() {
            assertEquals("c", selectionAfterMove(listOf("a", "b", "c"), "c", 1))
        }

        @Test
        fun `先頭で前へ移ろうとしても選択は変わらない`() {
            assertEquals("a", selectionAfterMove(listOf("a", "b", "c"), "a", -1))
        }

        @Test
        fun `選択が無ければ移動しない`() {
            assertNull(selectionAfterMove(listOf("a", "b", "c"), null, 1))
        }

        @Test
        fun `一覧に無いidを選択していると移動しない`() {
            assertEquals("x", selectionAfterMove(listOf("a", "b", "c"), "x", 1))
        }

        @Test
        fun `一覧が空なら移動しない`() {
            assertEquals("a", selectionAfterMove(emptyList(), "a", 1))
        }
    }

    @Nested
    inner class `selectionAfterChange` {

        @Test
        fun `追加があれば追加されたコミックを選ぶ`() {
            assertEquals("b", selectionAfterChange(setOf("a"), listOf("a", "b"), "a"))
        }

        @Test
        fun `複数まとめて追加されたらいちばん後ろを選ぶ`() {
            assertEquals("d", selectionAfterChange(setOf("a"), listOf("a", "b", "c", "d"), "a"))
        }

        @Test
        fun `選択が無い状態で追加があればそれを選ぶ`() {
            assertEquals("a", selectionAfterChange(emptySet(), listOf("a"), null))
        }

        @Test
        fun `選択中のコミックが消えたら先頭を選ぶ`() {
            assertEquals("a", selectionAfterChange(setOf("a", "b"), listOf("a"), "b"))
        }

        @Test
        fun `選択中のコミックが消えて一覧が空になったら選択を外す`() {
            assertNull(selectionAfterChange(setOf("a"), emptyList(), "a"))
        }

        @Test
        fun `一覧が変わっていなければ選択を保つ`() {
            assertEquals("b", selectionAfterChange(setOf("a", "b"), listOf("a", "b"), "b"))
        }

        @Test
        fun `削除だけなら残っている選択を保つ`() {
            assertEquals("a", selectionAfterChange(setOf("a", "b"), listOf("a"), "a"))
        }

        @Test
        fun `追加と削除が同時に起きたら追加を優先する`() {
            // マージのように1件消えて1件増える操作でも、取り込んだ側ではなく追加分へ移す。
            assertEquals("c", selectionAfterChange(setOf("a", "b"), listOf("a", "c"), "b"))
        }
    }

    @Nested
    inner class `followSelectionScrollTarget` {

        @Test
        fun `表示範囲に収まっているならスクロールしない`() {
            assertNull(
                followSelectionScrollTarget(
                    bounds = Rect(0f, 100f, 200f, 300f),
                    viewportHeight = 400,
                    currentScroll = 0,
                    maxScroll = 1000,
                )
            )
        }

        @Test
        fun `上に隠れているならカードの上端まで戻す`() {
            assertEquals(
                100,
                followSelectionScrollTarget(
                    bounds = Rect(0f, 100f, 200f, 300f),
                    viewportHeight = 400,
                    currentScroll = 250,
                    maxScroll = 1000,
                )
            )
        }

        @Test
        fun `下に隠れているならカードの下端が入る位置まで送る`() {
            // 下端 600 をビューポート高 400 に収めるので 200 までスクロールする。
            assertEquals(
                200,
                followSelectionScrollTarget(
                    bounds = Rect(0f, 400f, 200f, 600f),
                    viewportHeight = 400,
                    currentScroll = 0,
                    maxScroll = 1000,
                )
            )
        }

        @Test
        fun `スクロール量の上限を超えない`() {
            // 下端を収めるには 200 まで送りたいが、上限が 100 なのでそこで止める。
            assertEquals(
                100,
                followSelectionScrollTarget(
                    bounds = Rect(0f, 400f, 200f, 600f),
                    viewportHeight = 400,
                    currentScroll = 0,
                    maxScroll = 100,
                )
            )
        }

        @Test
        fun `カードの矩形が未確定ならスクロールしない`() {
            assertNull(
                followSelectionScrollTarget(
                    bounds = null,
                    viewportHeight = 400,
                    currentScroll = 0,
                    maxScroll = 1000,
                )
            )
        }

        @Test
        fun `ビューポートの高さが未確定ならスクロールしない`() {
            assertNull(
                followSelectionScrollTarget(
                    bounds = Rect(0f, 400f, 200f, 600f),
                    viewportHeight = 0,
                    currentScroll = 0,
                    maxScroll = 1000,
                )
            )
        }

        @Test
        fun `上限で止められた結果が現在位置と同じならスクロールしない`() {
            // すでに上限までスクロールしていて、それ以上は送れない状態。
            assertNull(
                followSelectionScrollTarget(
                    bounds = Rect(0f, 400f, 200f, 600f),
                    viewportHeight = 400,
                    currentScroll = 100,
                    maxScroll = 100,
                )
            )
        }
    }

    @Nested
    inner class `一括命名のテキスト` {

        @Test
        fun `1行をタブ区切りのid・著者名・題名として書き出す`() {
            val text = formatNameList(listOf(Triple("id1", "著者A", "題名A"), Triple("id2", "著者B", "題名B")))

            assertEquals("id1\t著者A\t題名A\nid2\t著者B\t題名B", text)
        }

        @Test
        fun `タブ区切りの3列を読み取る`() {
            val nameList = parseNameList("id1\t著者A\t題名A\nid2\t著者B\t題名B")

            assertEquals(listOf(Triple("id1", "著者A", "題名A"), Triple("id2", "著者B", "題名B")), nameList)
        }

        @Test
        fun `空行は捨てる`() {
            val nameList = parseNameList("id1\t著者A\t題名A\n\n   \n")

            assertEquals(listOf(Triple("id1", "著者A", "題名A")), nameList)
        }

        @Test
        fun `3列に分かれない行は捨てる`() {
            val nameList = parseNameList("id1\t著者A\nid2\t著者B\t題名B")

            assertEquals(listOf(Triple("id2", "著者B", "題名B")), nameList)
        }

        @Test
        fun `4列目以降は題名の一部として扱う`() {
            // 題名にタブが混ざっても列がずれないよう、3列目より後は分割しない。
            val nameList = parseNameList("id1\t著者A\t題名\tA")

            assertEquals(listOf(Triple("id1", "著者A", "題名\tA")), nameList)
        }

        @Test
        fun `書き出した結果を読み直すと元のリストへ戻る`() {
            val nameList = listOf(Triple("id1", "著者A", "題名A"), Triple("id2", "著者B", "題名B"))

            assertEquals(nameList, parseNameList(formatNameList(nameList)))
        }

        @Test
        fun `空のリストを書き出して読み直しても空のまま`() {
            assertEquals(emptyList<Triple<String, String, String>>(), parseNameList(formatNameList(emptyList())))
        }
    }

    @Nested
    inner class `shouldUseCutter` {

        @Test
        fun `横長の表紙全体があってアルバム表紙が無ければ切り出し画面`() {
            assertTrue(shouldUseCutter("coverF_001.jpg", null, isCoverFullLandscape = true))
        }

        @Test
        fun `アルバム表紙があれば切り出し済みなので詳細画面`() {
            assertFalse(shouldUseCutter("coverF_001.jpg", "coverA_001.jpg", isCoverFullLandscape = true))
        }

        @Test
        fun `表紙全体が縦長なら切り出す対象ではない`() {
            assertFalse(shouldUseCutter("coverF_001.jpg", null, isCoverFullLandscape = false))
        }

        @Test
        fun `表紙全体が無ければ切り出す対象ではない`() {
            assertFalse(shouldUseCutter(null, null, isCoverFullLandscape = true))
        }

        @Test
        fun `空文字のファイル名は無いものとして扱う`() {
            assertFalse(shouldUseCutter("", null, isCoverFullLandscape = true))
        }

        @Test
        fun `空文字のアルバム表紙は無いものとして扱う`() {
            assertTrue(shouldUseCutter("coverF_001.jpg", "", isCoverFullLandscape = true))
        }
    }
}
