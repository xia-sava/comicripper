package to.sava.comicripper.model

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class SettingTest {

    @TempDir
    lateinit var tempDir: Path

    private lateinit var originalHome: String

    @BeforeEach
    fun setup() {
        originalHome = System.getProperty("user.home")
        System.setProperty("user.home", tempDir.toString())
    }

    @AfterEach
    fun tearDown() {
        System.setProperty("user.home", originalHome)
        Setting.settingFile.delete()
    }

    @Test
    fun `saveしてloadでString値が復元される`() {
        Setting.workDirectory = "/test/work/dir"
        Setting.save()

        Setting.workDirectory = "default"
        assertTrue(Setting.load())

        assertEquals("/test/work/dir", Setting.workDirectory)
    }

    @Test
    fun `saveしてloadでDouble値が復元される`() {
        Setting.mainWindowWidth = 1234.5
        Setting.save()

        Setting.mainWindowWidth = 0.0
        Setting.load()

        assertEquals(1234.5, Setting.mainWindowWidth)
    }

    @Test
    fun `設定ファイルが存在しない場合loadはfalse`() {
        assertFalse(Setting.load())
    }

    @Test
    fun `var経由の設定変更がPropertyに反映される`() {
        Setting.workDirectory = "/new/path"

        assertEquals("/new/path", Setting.workDirectoryProperty.value)
    }

    @Test
    fun `Property経由の設定変更がvarに反映される`() {
        Setting.workDirectoryProperty.value = "/prop/path"

        assertEquals("/prop/path", Setting.workDirectory)
    }
}
