package to.sava.comicripper.ui.main

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ComicCardTest {

    @Nested
    inner class `truncateForDisplay` {

        @Test
        fun `maxLength 以下の文字列はそのまま返す`() {
            assertEquals("短い題名", truncateForDisplay("短い題名", 20))
        }

        @Test
        fun `maxLength ちょうどの文字列はそのまま返す`() {
            val text = "12345"
            assertEquals(text, truncateForDisplay(text, 5))
        }

        @Test
        fun `maxLength を超える文字列は先頭と末尾を残して中央を省略する`() {
            val text = "0123456789"

            // ellipsis " … "(3文字)を引いた残り3文字を前後に振り分ける: 前1文字・後2文字。
            assertEquals("0 … 89", truncateForDisplay(text, 6))
        }
    }

    @Nested
    inner class `fitSize` {

        @Test
        fun `幅が制約となる場合は幅いっぱいに縮小する`() {
            val (width, height) = fitSize(width = 400, height = 100, fitX = 200f, fitY = 200f)

            assertEquals(200f, width)
            assertEquals(50f, height)
        }

        @Test
        fun `高さが制約となる場合は高さいっぱいに縮小する`() {
            val (width, height) = fitSize(width = 100, height = 400, fitX = 200f, fitY = 200f)

            assertEquals(50f, width)
            assertEquals(200f, height)
        }

        @Test
        fun `枠より小さい画像は拡大される（アスペクト比維持）`() {
            val (width, height) = fitSize(width = 50, height = 100, fitX = 200f, fitY = 200f)

            assertEquals(100f, width)
            assertEquals(200f, height)
        }
    }
}
