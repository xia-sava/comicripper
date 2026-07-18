package to.sava.comicripper.ui

import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

/**
 * デスクトップ密度の Typography。
 * Material3 標準（モバイル前提）より1段小さいフォントサイズにする。
 */
private val DesktopTypography = Typography().run {
    copy(
        titleMedium = titleMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
        bodyLarge = bodyLarge.copy(fontSize = 13.sp, lineHeight = 18.sp),
        bodyMedium = bodyMedium.copy(fontSize = 12.sp, lineHeight = 16.sp),
        labelLarge = labelLarge.copy(fontSize = 12.sp, lineHeight = 16.sp),
    )
}

/**
 * 全 Compose ウィンドウ共通のテーマ。
 * タッチ操作用の最小コンポーネントサイズ（48dp）確保を無効化し、
 * デスクトップ密度の Typography を適用する。
 */
@Composable
fun ComicRipperTheme(content: @Composable () -> Unit) {
    MaterialTheme(typography = DesktopTypography) {
        CompositionLocalProvider(
            LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
            content = content,
        )
    }
}
