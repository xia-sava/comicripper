package to.sava.comicripper.ui

import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
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
 * Material3 標準の紫系アクセントに代えて使う、落ち着いたグリーン系の配色。
 * surfaceTint も primary に合わせておかないと、elevation の高いSurfaceに
 * 標準の紫の重ねがうっすら残る。
 */
private val DesktopColorScheme = lightColorScheme(
    primary = Color(0xFF3F6644),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC0EFC1),
    onPrimaryContainer = Color(0xFF002106),
    secondary = Color(0xFF52634F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD5E8CF),
    onSecondaryContainer = Color(0xFF111F0F),
    tertiary = Color(0xFF39656B),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFBCEBF2),
    onTertiaryContainer = Color(0xFF001F23),
    surfaceTint = Color(0xFF3F6644),
)

/**
 * 全 Compose ウィンドウ共通のテーマ。
 * タッチ操作用の最小コンポーネントサイズ（48dp）確保を無効化し、
 * デスクトップ密度の Typography と落ち着いたグリーン系の配色を適用する。
 */
@Composable
fun ComicRipperTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DesktopColorScheme, typography = DesktopTypography) {
        CompositionLocalProvider(
            LocalMinimumInteractiveComponentSize provides Dp.Unspecified,
            content = content,
        )
    }
}
