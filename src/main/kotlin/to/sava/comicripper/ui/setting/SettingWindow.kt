package to.sava.comicripper.ui.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.compose.koinInject
import to.sava.comicripper.model.Setting
import to.sava.comicripper.ui.BringToFrontOnFirstShow
import to.sava.comicripper.ui.ComicRipperTheme
import to.sava.comicripper.ui.ComicRipperWindow
import to.sava.comicripper.ui.CompactButton
import to.sava.comicripper.ui.CompactOutlinedTextField
import to.sava.comicripper.ui.rememberWindowIconPainter

/**
 * 設定画面。ウィンドウ位置・サイズと各設定値を Setting の Flow と同期する。
 */
@Composable
fun SettingWindow(onCloseRequest: () -> Unit, owner: java.awt.Window? = null) {
    val setting: Setting = koinInject()

    val state = rememberWindowState(
        size = DpSize(setting.settingWindowWidth.dp, setting.settingWindowHeight.dp),
        position = if (setting.settingWindowPosX >= 0.0) {
            WindowPosition.Absolute(setting.settingWindowPosX.dp, setting.settingWindowPosY.dp)
        } else {
            WindowPosition.PlatformDefault
        },
    )
    LaunchedEffect(state) {
        snapshotFlow { state.size }.collect { size ->
            setting.settingWindowWidth = size.width.value.toDouble()
            setting.settingWindowHeight = size.height.value.toDouble()
        }
    }
    LaunchedEffect(state) {
        snapshotFlow { state.position }.collect { position ->
            if (position is WindowPosition.Absolute) {
                setting.settingWindowPosX = position.x.value.toDouble()
                setting.settingWindowPosY = position.y.value.toDouble()
            }
        }
    }
    ComicRipperWindow(
        onCloseRequest = onCloseRequest,
        state = state,
        title = "設定",
        icon = rememberWindowIconPainter(),
        owner = owner,
    ) {
        BringToFrontOnFirstShow()
        ComicRipperTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SettingTextField("作業ディレクトリ", setting.workDirectoryFlow)
                    SettingTextField("格納ディレクトリ", setting.storeDirectoryFlow)
                    SettingTextField("Tesseract 実行ファイル", setting.TesseractExeFlow)
                    Spacer(modifier = Modifier.weight(1.0f))
                    CompactButton(
                        onClick = onCloseRequest,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text("閉じる")
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingTextField(label: String, flow: MutableStateFlow<String>) {
    var text by remember { mutableStateOf(flow.value) }
    CompactOutlinedTextField(
        value = text,
        onValueChange = { newValue ->
            text = newValue
            flow.value = newValue
        },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
    )
}
