package to.sava.comicripper.infrastructure.repository

import androidx.compose.runtime.snapshots.Snapshot
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import to.sava.comicripper.domain.model.Comic
import to.sava.comicripper.model.Setting
import to.sava.comicripper.model.quarantineBrokenFile
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Properties

private val logger = KotlinLogging.logger {}

private val structureJson = Json {
    prettyPrint = true
    // 新しいバージョンで追加されたキーを含むファイルを旧バージョンでも読めるようにする。
    ignoreUnknownKeys = true
}

/** JSON永続化用の構造ファイルのComic1件分のスナップショット。 */
@Serializable
private data class ComicStructureEntry(
    val id: String,
    val author: String,
    val title: String,
    val files: List<String>,
)

@Serializable
private data class ComicStructureData(
    val comics: List<ComicStructureEntry> = emptyList(),
)

/**
 * どのファイルがどのコミックに属するかを作業ディレクトリへ永続化する。
 * 作業ディレクトリごとのデータなので、置き場所は [Setting.structureFile] が決める。
 */
class StructureStore(private val setting: Setting, private val comicStorage: ComicStorage) {

    // プロセスが書き込み中に強制終了しても壊れたファイルが残らないよう、
    // 同一ディレクトリの一時ファイルへ書いてから rename で置き換える。
    fun save() {
        // 別スレッドの操作の途中経過を書かないよう、ある一時点のスナップショットから読み取る。
        val snapshot = Snapshot.takeSnapshot()
        val data = try {
            snapshot.enter {
                ComicStructureData(
                    comics = comicStorage.all.map { comic ->
                        ComicStructureEntry(comic.id, comic.author, comic.title, comic.files)
                    }
                )
            }
        } finally {
            snapshot.dispose()
        }
        val text = structureJson.encodeToString(ComicStructureData.serializer(), data)
        val structureFile = setting.structureFile
        val tempFile = File.createTempFile("comicripperStructure", ".tmp", structureFile.absoluteFile.parentFile)
        try {
            tempFile.writeText(text)
            Files.move(
                tempFile.toPath(),
                structureFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } finally {
            tempFile.delete()
        }
    }

    /**
     * 構造を読み込む。JSON形式ファイルがあればそれを使い、無く旧Properties形式ファイルが
     * あれば読み込んでJSON形式で保存し直し、旧ファイルは `.bak` へリネームして残す。
     */
    fun load(): Boolean {
        if (setting.structureFile.isFile) {
            return runCatching {
                applyData(structureJson.decodeFromString(ComicStructureData.serializer(), setting.structureFile.readText()))
            }.onFailure {
                logger.error(it) { "structure load failed" }
                quarantineBrokenFile(setting.structureFile)
            }.isSuccess
        }
        if (setting.legacyStructureFile.isFile) {
            return loadLegacyAndMigrate()
        }
        return false
    }

    private fun applyData(data: ComicStructureData) {
        val comics = data.comics.map { entry ->
            Comic(id = entry.id).apply {
                author = entry.author
                title = entry.title
                addFiles(entry.files.filter { File("${setting.workDirectory}/$it").exists() })
            }
        }
        comicStorage.add(*comics.filter { it.files.isNotEmpty() }.toTypedArray())
    }

    private fun loadLegacyAndMigrate(): Boolean {
        val loaded = runCatching {
            val props = Properties()
            setting.legacyStructureFile.inputStream().use { props.load(it) }
            applyLegacyProperties(props)
        }.onFailure { logger.warn(it) { "legacy structure load failed" } }.isSuccess
        if (!loaded) {
            return false
        }
        runCatching {
            save()
            val backup = File("${setting.legacyStructureFile.path}.bak")
            Files.move(setting.legacyStructureFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }.onFailure { logger.warn(it) { "legacy structure migration failed" } }
        return true
    }

    private fun applyLegacyProperties(props: Properties) {
        props.propertyNames().toList().map { it as String }
            .filter { it.startsWith("_") }
            .associate {
                val id = it.trimStart('_')
                val (index, author, title) = props.getProperty(it).split("\t")
                val comic = Comic(id = id).apply {
                    this.author = author
                    this.title = title
                }
                index.toInt() to comic
            }
            .toSortedMap()
            .forEach {
                comicStorage.add(it.value)
            }
        props.propertyNames().toList()
            .map { it as String }
            .filterNot { it.startsWith("_") }
            .sorted()
            .forEach { filename ->
                if (File("${setting.workDirectory}/$filename").exists()) {
                    val comicId = props.getProperty(filename)
                    val baseComic = comicStorage[comicId]
                    if (baseComic != null) {
                        baseComic.addFile(filename)
                    } else {
                        comicStorage.add(Comic(filename))
                    }
                }
            }
        comicStorage.all.filter { it.files.isEmpty() }.forEach {
            comicStorage.remove(it)
        }
    }
}
