package to.sava.comicripper.repository

import to.sava.comicripper.model.Comic
import to.sava.comicripper.model.Setting
import java.io.File

class ComicRepository {
    suspend fun exampleListComic(): Sequence<Comic> {
        val dir = File(Setting.workDirectory)
        return sequence {
            dir.listFiles { file -> file.name.endsWith(".jpg") }?.map {
                val url = it.toURI().toURL().toString()
                val comic = Comic(it.name).apply {
                    when {
                        it.name.startsWith("coverA") -> coverFront = url
                        it.name.startsWith("coverF") -> coverAll = url
                        it.name.startsWith("coverS") -> coverBelt = url
                        it.name.startsWith("page") -> pagesProperty.add(url)
                    }
                }
                yield(comic)
            } ?: listOf()
        }
    }
}