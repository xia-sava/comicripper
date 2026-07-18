package to.sava.comicripper.ui

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

private val CompactButtonHeight = 28.dp
private val CompactButtonPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)

/** decoration 内部のテキスト行最小高 24dp + 上下パディング 4dp ずつ */
private val CompactTextFieldMinHeight = 32.dp
private val CompactTextFieldPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)

/** ラベル使用時、上枠線に浮くラベルの分だけ外側に足す上余白 */
private val CompactTextFieldLabelTopPadding = 8.dp

private val CompactSliderThumbSize = DpSize(4.dp, 24.dp)

/**
 * デスクトップ密度のボタン。
 * 高さを tight 制約で 28dp に固定して Material3 の最小高 40dp を打ち消す。
 * ComicRipperTheme（LocalMinimumInteractiveComponentSize = Unspecified）配下で使うこと。
 */
@Composable
fun CompactButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(CompactButtonHeight),
        enabled = enabled,
        shape = MaterialTheme.shapes.extraSmall,
        contentPadding = CompactButtonPadding,
        content = content,
    )
}

/**
 * デスクトップ密度の1行テキストフィールド。
 * OutlinedTextField は最小高 56dp とパディング 16dp を変更できないため、
 * BasicTextField + OutlinedTextFieldDefaults.DecorationBox で構成する。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val textStyle = LocalTextStyle.current.merge(
        TextStyle(color = MaterialTheme.colorScheme.onSurface)
    )
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .then(
                if (label != null) {
                    Modifier.padding(top = CompactTextFieldLabelTopPadding)
                } else {
                    Modifier
                }
            )
            .defaultMinSize(minHeight = CompactTextFieldMinHeight),
        textStyle = textStyle,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        singleLine = singleLine,
        interactionSource = interactionSource,
        decorationBox = { innerTextField ->
            OutlinedTextFieldDefaults.DecorationBox(
                value = value,
                innerTextField = innerTextField,
                enabled = true,
                singleLine = singleLine,
                visualTransformation = VisualTransformation.None,
                interactionSource = interactionSource,
                label = label,
                contentPadding = CompactTextFieldPadding,
            )
        },
    )
}

/**
 * デスクトップ密度のスライダー。
 * thumb を 4x24dp に縮小して全高を 24dp にする（標準 thumb は 4x44dp）。
 * ComicRipperTheme（LocalMinimumInteractiveComponentSize = Unspecified）配下で使うこと。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    steps: Int = 0,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        interactionSource = interactionSource,
        steps = steps,
        thumb = {
            SliderDefaults.Thumb(
                interactionSource = interactionSource,
                thumbSize = CompactSliderThumbSize,
            )
        },
        valueRange = valueRange,
    )
}
