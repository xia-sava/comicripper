package to.sava.comicripper.model

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.Properties

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

    @Nested
    inner class `旧Properties形式からの自動移行` {

        private fun legacyFile() = File("$tempDir/.comicripper")

        private fun writeLegacyProperties(entries: Map<String, String>) {
            val props = Properties()
            entries.forEach { (k, v) -> props.setProperty(k, v) }
            legacyFile().outputStream().use { props.store(it, null) }
        }

        @Test
        fun `旧形式のみ存在する場合は読み込んでJSON形式に移行する`() {
            writeLegacyProperties(mapOf("workDirectory" to "/legacy/dir", "mainWindowWidth" to "999.0"))

            assertTrue(setting.load())

            assertEquals("/legacy/dir", setting.workDirectory)
            assertEquals(999.0, setting.mainWindowWidth)
            assertTrue(setting.settingFile.exists(), "JSON形式ファイルが作られているはず")
        }

        @Test
        fun `移行後は旧ファイルが bak にリネームされる`() {
            writeLegacyProperties(mapOf("workDirectory" to "/legacy/dir"))

            setting.load()

            assertFalse(legacyFile().exists(), "旧ファイルは残っていないはず")
            assertTrue(File("${legacyFile().path}.bak").exists(), ".bak にリネームされているはず")
        }

        @Test
        fun `新旧両方存在する場合はJSON形式が優先される`() {
            writeLegacyProperties(mapOf("workDirectory" to "/legacy/dir"))
            setting.workDirectory = "/json/dir"
            setting.save()

            setting.workDirectory = "default"
            assertTrue(setting.load())

            assertEquals("/json/dir", setting.workDirectory)
            assertTrue(legacyFile().exists(), "JSON形式がある場合は旧ファイルへ触れないはず")
        }
    }
}
