package to.sava.comicripper.infrastructure.text

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class NameNormalizationTest {

    @Nested
    inner class `normalizeText` {

        @Test
        fun `全角英数はNFKCで半角へ寄せる`() {
            assertEquals("A", normalizeText("Ａ"))
        }

        @Test
        fun `ファイル名に使えない文字は全角へ置き換える`() {
            assertEquals("test？file", normalizeText("test?file"))
        }

        @Test
        fun `全角で入力された禁止文字も全角のまま残す`() {
            // NFKCで半角へ寄ってから全角へ戻るので、入力の幅に依らず同じ結果になる。
            assertEquals("test？file", normalizeText("test？file"))
        }

        @Test
        fun `パス区切りに見える文字も置き換える`() {
            assertEquals("上／下", normalizeText("上/下"))
        }
    }

    @Nested
    inner class `normalizeBookName` {

        @Test
        fun `丸括弧の巻数が統一される`() {
            val (_, title) = normalizeBookName(listOf("著者"), "タイトル(1)")
            assertEquals("タイトル (1)", title)
        }

        @Test
        fun `角括弧の巻数が統一される`() {
            val (_, title) = normalizeBookName(listOf("著者"), "タイトル[2]")
            assertEquals("タイトル (2)", title)
        }

        @Test
        fun `波括弧の巻数が統一される`() {
            val (_, title) = normalizeBookName(listOf("著者"), "タイトル{3}")
            assertEquals("タイトル (3)", title)
        }

        @Test
        fun `第n巻の表記から巻数を取り出す`() {
            val (_, title) = normalizeBookName(listOf("著者"), "タイトル 第5巻")
            assertEquals("タイトル (5)", title)
        }

        @Test
        fun `複数著者はスラッシュで連結される`() {
            val (author, _) = normalizeBookName(listOf("著者A", "著者B"), "タイトル")
            assertEquals("著者A／著者B", author)
        }

        @Test
        fun `著者名の空白は詰める`() {
            val (author, _) = normalizeBookName(listOf("著 者 A"), "タイトル")
            assertEquals("著者A", author)
        }
    }
}
