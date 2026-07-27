package to.sava.comicripper.model

import androidx.compose.runtime.snapshots.Snapshot
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
        setting.dataDirectory = tempDir.resolve("appdata").toFile()
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
        setting.mainWindow.width = 1234.5
        setting.save()

        setting.mainWindow.width = 0.0
        setting.load()

        assertEquals(1234.5, setting.mainWindow.width)
    }

    @Test
    fun `設定ファイルが存在しない場合loadはfalse`() {
        assertFalse(setting.load())
    }

    /**
     * 取得済みスナップショットから変更が見えないことで、設定が snapshot state で保持されている
     * （= 画面が変更を検知して再コンポーズできる）ことを確かめる。
     */
    @Test
    fun `設定変更は取得済みスナップショットには見えない`() {
        setting.workDirectory = "/before"

        val snapshot = Snapshot.takeSnapshot()
        try {
            setting.workDirectory = "/after"

            assertEquals("/before", snapshot.enter { setting.workDirectory })
            assertEquals("/after", setting.workDirectory)
        } finally {
            snapshot.dispose()
        }
    }

    @Test
    fun `全設定項目がsaveとloadでラウンドトリップする`() {
        // ウィンドウごとの項目は永続化形式との対応を手で書いているため、
        // 4ウィンドウ×4項目すべてに異なる値を入れて取り違えを検出できるようにする。
        val geometries = listOf(setting.mainWindow, setting.detailWindow, setting.cutterWindow, setting.settingWindow)
        geometries.forEachIndexed { index, geometry ->
            val base = (index + 1) * 1000.0
            geometry.width = base + 1
            geometry.height = base + 2
            geometry.posX = base + 3
            geometry.posY = base + 4
        }
        setting.workDirectory = "/round/trip"
        setting.storeDirectory = "/store/trip"
        setting.googleBookApi = "https://example.com/books?isbn="
        setting.YodobashiSearchUrl = "https://example.com/search?word="
        setting.TesseractExe = "/usr/bin/tesseract"
        setting.cutterLeftPercent = 20.0
        setting.cutterRightPercent = 60.0
        setting.save()

        geometries.forEach { geometry ->
            geometry.width = 0.0
            geometry.height = 0.0
            geometry.posX = 0.0
            geometry.posY = 0.0
        }
        setting.workDirectory = ""
        setting.storeDirectory = ""
        setting.googleBookApi = ""
        setting.YodobashiSearchUrl = ""
        setting.TesseractExe = ""
        setting.cutterLeftPercent = 0.0
        setting.cutterRightPercent = 0.0
        setting.load()

        geometries.forEachIndexed { index, geometry ->
            val base = (index + 1) * 1000.0
            assertEquals(base + 1, geometry.width, "ウィンドウ$index の幅")
            assertEquals(base + 2, geometry.height, "ウィンドウ$index の高さ")
            assertEquals(base + 3, geometry.posX, "ウィンドウ$index のX位置")
            assertEquals(base + 4, geometry.posY, "ウィンドウ$index のY位置")
        }
        assertEquals("/round/trip", setting.workDirectory)
        assertEquals("/store/trip", setting.storeDirectory)
        assertEquals("https://example.com/books?isbn=", setting.googleBookApi)
        assertEquals("https://example.com/search?word=", setting.YodobashiSearchUrl)
        assertEquals("/usr/bin/tesseract", setting.TesseractExe)
        assertEquals(20.0, setting.cutterLeftPercent)
        assertEquals(60.0, setting.cutterRightPercent)
    }

    @Test
    fun `壊れたJSONはloadがfalseになりbrokenへ退避される`() {
        setting.settingFile.parentFile.mkdirs()
        setting.settingFile.writeText("{ broken json ")

        assertFalse(setting.load())

        assertFalse(setting.settingFile.exists(), "壊れたファイルは元の場所に残らないはず")
        assertTrue(File("${setting.settingFile.path}.broken").exists(), ".broken へ退避されているはず")
    }

    @Test
    fun `未知のキーを含むJSONも読み込める`() {
        setting.settingFile.parentFile.mkdirs()
        setting.settingFile.writeText("""{"workDirectory": "/known/dir", "unknownFutureKey": 123}""")

        assertTrue(setting.load())

        assertEquals("/known/dir", setting.workDirectory)
    }

    @Test
    fun `structureFileがworkDirectory配下にある`() {
        setting.workDirectory = "/some/dir"
        assertTrue(setting.structureFile.path.replace("\\", "/").startsWith("/some/dir/"))
    }

    @Nested
    inner class `ホームディレクトリ直下JSONからの自動移行` {

        private fun homeJsonFile() = File("$tempDir/.comicripper.json")

        @Test
        fun `ホーム直下JSONのみ存在する場合は読み込んで現行の置き場所に移行する`() {
            setting.workDirectory = "/home/json/dir"
            val text = setting.settingFile.let {
                // save() で一旦シリアライズしてホーム直下へ移し、現行の置き場所からは消しておく。
                setting.save()
                val t = it.readText()
                it.delete()
                t
            }
            homeJsonFile().writeText(text)
            setting.workDirectory = "default"

            assertTrue(setting.load())

            assertEquals("/home/json/dir", setting.workDirectory)
            assertTrue(setting.settingFile.isFile, "現行の置き場所にファイルが作られているはず")
            assertFalse(homeJsonFile().exists(), "ホーム直下のファイルは残っていないはず")
            assertTrue(File("${homeJsonFile().path}.bak").exists(), ".bak にリネームされているはず")
        }

        @Test
        fun `現行の置き場所とホーム直下の両方に存在する場合は現行が優先される`() {
            homeJsonFile().writeText("""{"workDirectory": "/home/json/dir"}""")
            setting.workDirectory = "/current/dir"
            setting.save()

            setting.workDirectory = "default"
            assertTrue(setting.load())

            assertEquals("/current/dir", setting.workDirectory)
            assertTrue(homeJsonFile().exists(), "現行の置き場所にある場合はホーム直下へ触れないはず")
        }
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
            assertEquals(999.0, setting.mainWindow.width)
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
