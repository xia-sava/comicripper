package to.sava.comicripper.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.awt.image.BufferedImage

class ComposeExtTest {

    @Nested
    inner class `toDisplayImageBitmap` {

        @ParameterizedTest
        @ValueSource(ints = [0, 32, 128, 192, 255])
        fun `グレースケール画像は画素値を保ったまま変換される`(gray: Int) {
            val image = BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY).apply {
                raster.setSample(0, 0, 0, gray)
            }

            val pixel = image.toDisplayImageBitmap().toPixelMap()[0, 0]

            assertEquals(Color(gray, gray, gray), pixel)
        }

        @Test
        fun `カラー画像は色を保ったまま変換される`() {
            val image = BufferedImage(1, 1, BufferedImage.TYPE_3BYTE_BGR).apply {
                setRGB(0, 0, 0x336699)
            }

            val pixel = image.toDisplayImageBitmap().toPixelMap()[0, 0]

            assertEquals(Color(0x33, 0x66, 0x99), pixel)
        }
    }
}
