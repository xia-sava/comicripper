package to.sava.comicripper.model

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class SettingTest {

    private val setting = Setting()

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
        setting.settingFile.delete()
    }

    @Test
    fun `saveしてloadでString値が復元される`() {
        setting.workDirectory = "/test/work/dir"
        setting.save()

        setting.workDirectory = "default"
        assertTrue(setting.load())

        assertEquals("/test/work/dir", setting.workDirectory)
    }

    @Test
    fun `saveしてloadでDouble値が復元される`() {
        setting.mainWindowWidth = 1234.5
        setting.save()

        setting.mainWindowWidth = 0.0
        setting.load()

        assertEquals(1234.5, setting.mainWindowWidth)
    }

    @Test
    fun `設定ファイルが存在しない場合loadはfalse`() {
        assertFalse(setting.load())
    }

    @Test
    fun `var経由の設定変更がFlowに反映される`() {
        setting.workDirectory = "/new/path"

        assertEquals("/new/path", setting.workDirectoryFlow.value)
    }

    @Test
    fun `Flow経由の設定変更がvarに反映される`() {
        setting.workDirectoryFlow.value = "/prop/path"

        assertEquals("/prop/path", setting.workDirectory)
    }

    @Test
    fun `全設定項目がsaveとloadでラウンドトリップする`() {
        setting.mainWindowWidth = 111.0
        setting.mainWindowHeight = 222.0
        setting.workDirectory = "/round/trip"
        setting.storeDirectory = "/store/trip"
        setting.cutterLeftPercent = 20.0
        setting.cutterRightPercent = 60.0
        setting.save()

        setting.mainWindowWidth = 0.0
        setting.mainWindowHeight = 0.0
        setting.workDirectory = ""
        setting.storeDirectory = ""
        setting.cutterLeftPercent = 0.0
        setting.cutterRightPercent = 0.0
        setting.load()

        assertEquals(111.0, setting.mainWindowWidth)
        assertEquals(222.0, setting.mainWindowHeight)
        assertEquals("/round/trip", setting.workDirectory)
        assertEquals("/store/trip", setting.storeDirectory)
        assertEquals(20.0, setting.cutterLeftPercent)
        assertEquals(60.0, setting.cutterRightPercent)
    }

    @Test
    fun `structureFileがworkDirectory配下にある`() {
        setting.workDirectory = "/some/dir"
        assertTrue(setting.structureFile.path.replace("\\", "/").startsWith("/some/dir/"))
    }
}
