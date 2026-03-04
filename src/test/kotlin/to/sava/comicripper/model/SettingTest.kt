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
    fun `var経由の設定変更がFlowに反映される`() {
        Setting.workDirectory = "/new/path"

        assertEquals("/new/path", Setting.workDirectoryFlow.value)
    }

    @Test
    fun `Flow経由の設定変更がvarに反映される`() {
        Setting.workDirectoryFlow.value = "/prop/path"

        assertEquals("/prop/path", Setting.workDirectory)
    }

    @Test
    fun `全設定項目がsaveとloadでラウンドトリップする`() {
        Setting.mainWindowWidth = 111.0
        Setting.mainWindowHeight = 222.0
        Setting.workDirectory = "/round/trip"
        Setting.storeDirectory = "/store/trip"
        Setting.cutterLeftPercent = 20.0
        Setting.cutterRightPercent = 60.0
        Setting.save()

        Setting.mainWindowWidth = 0.0
        Setting.mainWindowHeight = 0.0
        Setting.workDirectory = ""
        Setting.storeDirectory = ""
        Setting.cutterLeftPercent = 0.0
        Setting.cutterRightPercent = 0.0
        Setting.load()

        assertEquals(111.0, Setting.mainWindowWidth)
        assertEquals(222.0, Setting.mainWindowHeight)
        assertEquals("/round/trip", Setting.workDirectory)
        assertEquals("/store/trip", Setting.storeDirectory)
        assertEquals(20.0, Setting.cutterLeftPercent)
        assertEquals(60.0, Setting.cutterRightPercent)
    }

    @Test
    fun `structureFileがworkDirectory配下にある`() {
        Setting.workDirectory = "/some/dir"
        assertTrue(Setting.structureFile.path.replace("\\", "/").startsWith("/some/dir/"))
    }
}
