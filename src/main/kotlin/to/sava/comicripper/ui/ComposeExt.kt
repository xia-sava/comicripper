package to.sava.comicripper.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import to.sava.comicripper.ext.Loader
import javax.imageio.ImageIO

/**
 * アイコン PNG の Painter を作成（Stage.setWindowIcon() の Compose 版）
 */
@Composable
fun rememberWindowIconPainter(): Painter? = remember {
    Loader.javaClass.getResourceAsStream("/to/sava/comicripper/icon.png")
        ?.use { ImageIO.read(it) }
        ?.toComposeImageBitmap()
        ?.let(::BitmapPainter)
}
