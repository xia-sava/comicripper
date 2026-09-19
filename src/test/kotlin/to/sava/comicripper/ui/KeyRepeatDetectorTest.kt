package to.sava.comicripper.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KeyRepeatDetectorTest {

    @Test
    fun `最初の押下はリピートではない`() {
        val detector = KeyRepeatDetector()

        assertFalse(detector.onKeyEvent(KeyEventType.KeyDown, Key.D))
    }

    @Test
    fun `離さずに続けて届いた押下はリピート`() {
        val detector = KeyRepeatDetector()
        detector.onKeyEvent(KeyEventType.KeyDown, Key.D)

        assertTrue(detector.onKeyEvent(KeyEventType.KeyDown, Key.D))
    }

    @Test
    fun `離してから押し直すとリピートではない`() {
        val detector = KeyRepeatDetector()
        detector.onKeyEvent(KeyEventType.KeyDown, Key.D)
        detector.onKeyEvent(KeyEventType.KeyUp, Key.D)

        assertFalse(detector.onKeyEvent(KeyEventType.KeyDown, Key.D))
    }

    @Test
    fun `修飾キーを押したままでも別のキーの押下はリピートではない`() {
        val detector = KeyRepeatDetector()
        detector.onKeyEvent(KeyEventType.KeyDown, Key.CtrlLeft)

        assertFalse(detector.onKeyEvent(KeyEventType.KeyDown, Key.D))
    }

    @Test
    fun `状態を捨てると押したままだったキーの次の押下はリピートではない`() {
        // 押したままフォーカスが外れ、離したことを受け取れなかった場合にあたる。
        val detector = KeyRepeatDetector()
        detector.onKeyEvent(KeyEventType.KeyDown, Key.D)
        detector.reset()

        assertFalse(detector.onKeyEvent(KeyEventType.KeyDown, Key.D))
    }
}
