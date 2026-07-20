package to.sava.comicripper.ui.cutter

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class CutterWindowTest {

    @Nested
    inner class `imageDisplayRect` {

        @Test
        fun `コンテナと同じアスペクト比なら全面に表示される`() {
            val rect = imageDisplayRect(containerWidth = 400f, containerHeight = 200f, imageWidth = 800, imageHeight = 400)

            assertEquals(0f, rect.left)
            assertEquals(0f, rect.top)
            assertEquals(400f, rect.right)
            assertEquals(200f, rect.bottom)
        }

        @Test
        fun `横長画像はコンテナ幅に合わせて上下にレターボックスが空く`() {
            // 画像は 2:1、コンテナは 1:1 なので幅基準で縮小され、上下に余白ができる。
            val rect = imageDisplayRect(containerWidth = 200f, containerHeight = 200f, imageWidth = 400, imageHeight = 200)

            assertEquals(0f, rect.left)
            assertEquals(200f, rect.right)
            assertEquals(50f, rect.top)
            assertEquals(150f, rect.bottom)
        }

        @Test
        fun `縦長画像はコンテナ高さに合わせて左右にレターボックスが空く`() {
            // 画像は 1:2、コンテナは 1:1 なので高さ基準で縮小され、左右に余白ができる。
            val rect = imageDisplayRect(containerWidth = 200f, containerHeight = 200f, imageWidth = 200, imageHeight = 400)

            assertEquals(0f, rect.top)
            assertEquals(200f, rect.bottom)
            assertEquals(50f, rect.left)
            assertEquals(150f, rect.right)
        }
    }
}
