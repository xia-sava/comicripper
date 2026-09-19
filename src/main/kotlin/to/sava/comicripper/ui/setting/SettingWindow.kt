package to.sava.comicripper.ui.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import to.sava.comicripper.model.Setting
import to.sava.comicripper.ui.BringToFrontOnShow
import to.sava.comicripper.ui.ComicRipperTheme
import to.sava.comicripper.ui.ComicRipperWindow
import to.sava.comicripper.ui.CompactButton
import to.sava.comicripper.ui.CompactOutlinedTextField
import to.sava.comicripper.ui.rememberPersistedWindowState
import to.sava.comicripper.ui.rememberWindowIconPainter

/**
 * 設定画面。ウィンドウ位置・サイズと各設定値を Setting へ直接読み書きする。
 */
@Composable
fun SettingWindow(onCloseRequest: () -> Unit, owner: java.awt.Window? = null) {
    val setting: Setting = koinInject()

    val state = rememberPersistedWindowState(setting.settingWindow)
    ComicRipperWindow(
        onCloseRequest = onCloseRequest,
        state = state,
        title = "設定",
        icon = rememberWindowIconPainter(),
        owner = owner,
    ) {
        BringToFrontOnShow()
        ComicRipperTheme {
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SettingTextField("作業ディレクトリ", setting.workDirectory) { setting.workDirectory = it }
                    Text("作業ディレクトリの変更は次回起動時に反映されます", style = MaterialTheme.typography.bodySmall)
                    SettingTextField("格納ディレクトリ", setting.storeDirectory) { setting.storeDirectory = it }
                    SettingTextField("Tesseract 実行ファイル", setting.tesseractExe) { setting.tesseractExe = it }
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
private fun SettingTextField(label: String, value: String, onValueChange: (String) -> Unit) {
    CompactOutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
    )
}
