package to.sava.comicripper.ui

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressOverlayStateTest {

    @Test
    fun `開始するとオーバーレイが表示される`() = runTest {
        val state = ProgressOverlayState(this, mutableStateOf({ _: String -> }))

        state.launchTask("OCRしています", "説明") { delay(10_000) }
        runCurrent()

        assertTrue(state.isActive)
        assertEquals("OCRしています", state.task?.title)
        state.cancel()
    }

    @Test
    fun `中止すると実行中のタスクが取り消されオーバーレイが消える`() = runTest {
        val state = ProgressOverlayState(this, mutableStateOf({ _: String -> }))
        var finished = false

        state.launchTask("OCRしています", "説明") {
            delay(10_000)
            finished = true
        }
        runCurrent()

        state.cancel()
        runCurrent()

        assertFalse(state.isActive)
        assertFalse(finished)
    }

    @Test
    fun `実行中は次のタスクを開始しない`() = runTest {
        val state = ProgressOverlayState(this, mutableStateOf({ _: String -> }))
        var startedCount = 0

        state.launchTask("1つ目", "説明") {
            startedCount++
            delay(10_000)
        }
        runCurrent()
        state.launchTask("2つ目", "説明") {
            startedCount++
            delay(10_000)
        }
        runCurrent()

        assertEquals(1, startedCount)
        assertEquals("1つ目", state.task?.title)
        state.cancel()
    }

    @Test
    fun `例外で終わると失敗として通知されオーバーレイが消える`() = runTest {
        var notifiedTitle: String? = null
        val state = ProgressOverlayState(this, mutableStateOf({ title: String -> notifiedTitle = title }))

        state.launchTask("ZIPしています", "説明") { error("失敗") }
        runCurrent()

        assertEquals("ZIPしています", notifiedTitle)
        assertFalse(state.isActive)
    }

    @Test
    fun `中止は失敗として通知しない`() = runTest {
        var notifiedTitle: String? = null
        val state = ProgressOverlayState(this, mutableStateOf({ title: String -> notifiedTitle = title }))

        state.launchTask("ZIPしています", "説明") { delay(10_000) }
        runCurrent()

        state.cancel()
        runCurrent()

        assertEquals(null, notifiedTitle)
    }
}
