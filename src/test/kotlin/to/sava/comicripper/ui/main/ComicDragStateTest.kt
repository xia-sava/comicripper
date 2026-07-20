package to.sava.comicripper.ui.main

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class ComicDragStateTest {

    private fun boundsAt(origin: Offset, size: Size = Size(100f, 100f)) =
        Rect(origin, size)

    @Nested
    inner class `ドラッグ開始と終了` {

        @Test
        fun `start() でドラッグ元が記録される`() {
            val state = ComicDragState()
            state.start("a")
            assertEquals("a", state.draggingId)
            assertNull(state.dropTargetId)
        }

        @Test
        fun `end() は開始前は何も起きない`() {
            val state = ComicDragState()
            assertNull(state.end())
        }

        @Test
        fun `cancel() でドラッグ状態がクリアされる`() {
            val state = ComicDragState()
            state.start("a")
            state.cancel()
            assertNull(state.draggingId)
            assertNull(state.dropTargetId)
        }
    }

    @Nested
    inner class `ヒットテスト` {

        @Test
        fun `drag() はポインタが重なるカードを dropTargetId に設定する`() {
            val state = ComicDragState()
            state.register("a", origin = Offset(0f, 0f), clippedBounds = boundsAt(Offset(0f, 0f)))
            state.register("b", origin = Offset(200f, 0f), clippedBounds = boundsAt(Offset(200f, 0f)))
            state.start("a")

            // a のローカル座標 (250, 50) はウィンドウ座標 (0,0)+(250,50) = (250,50) で b の矩形内。
            state.drag(Offset(250f, 50f))

            assertEquals("b", state.dropTargetId)
        }

        @Test
        fun `drag() は自分自身を dropTargetId にしない`() {
            val state = ComicDragState()
            state.register("a", origin = Offset(0f, 0f), clippedBounds = boundsAt(Offset(0f, 0f)))
            state.start("a")

            state.drag(Offset(10f, 10f))

            assertNull(state.dropTargetId)
        }

        @Test
        fun `drag() はどのカードとも重ならなければ dropTargetId を null に戻す`() {
            val state = ComicDragState()
            state.register("a", origin = Offset(0f, 0f), clippedBounds = boundsAt(Offset(0f, 0f)))
            state.register("b", origin = Offset(200f, 0f), clippedBounds = boundsAt(Offset(200f, 0f)))
            state.start("a")
            state.drag(Offset(250f, 50f))
            assertEquals("b", state.dropTargetId)

            // ウィンドウ座標 (900, 900) はどちらの矩形にも含まれない。
            state.drag(Offset(900f, 900f))

            assertNull(state.dropTargetId)
        }
    }

    @Nested
    inner class `ドロップ確定` {

        @Test
        fun `end() はドロップ先があれば (src, dst) を返して状態をクリアする`() {
            val state = ComicDragState()
            state.register("a", origin = Offset(0f, 0f), clippedBounds = boundsAt(Offset(0f, 0f)))
            state.register("b", origin = Offset(200f, 0f), clippedBounds = boundsAt(Offset(200f, 0f)))
            state.start("a")
            state.drag(Offset(250f, 50f))

            val result = state.end()

            assertEquals("a" to "b", result)
            assertNull(state.draggingId)
            assertNull(state.dropTargetId)
        }

        @Test
        fun `end() はドロップ先が無ければ null を返す`() {
            val state = ComicDragState()
            state.register("a", origin = Offset(0f, 0f), clippedBounds = boundsAt(Offset(0f, 0f)))
            state.start("a")

            assertNull(state.end())
        }
    }

    @Nested
    inner class `unregister によるクリーンアップ` {

        @Test
        fun `ドラッグ中のカードが unregister されるとドラッグ状態が消える`() {
            val state = ComicDragState()
            state.register("a", origin = Offset(0f, 0f), clippedBounds = boundsAt(Offset(0f, 0f)))
            state.start("a")

            state.unregister("a")

            assertNull(state.draggingId)
        }

        @Test
        fun `ドロップ先候補のカードが unregister されると dropTargetId が消える`() {
            val state = ComicDragState()
            state.register("a", origin = Offset(0f, 0f), clippedBounds = boundsAt(Offset(0f, 0f)))
            state.register("b", origin = Offset(200f, 0f), clippedBounds = boundsAt(Offset(200f, 0f)))
            state.start("a")
            state.drag(Offset(250f, 50f))
            assertEquals("b", state.dropTargetId)

            state.unregister("b")

            assertNull(state.dropTargetId)
        }

        @Test
        fun `unregister 後は同じ位置に drag しても再ヒットしない`() {
            val state = ComicDragState()
            state.register("a", origin = Offset(0f, 0f), clippedBounds = boundsAt(Offset(0f, 0f)))
            state.register("b", origin = Offset(200f, 0f), clippedBounds = boundsAt(Offset(200f, 0f)))
            state.start("a")
            state.unregister("b")

            state.drag(Offset(250f, 50f))

            assertNull(state.dropTargetId)
        }
    }
}
