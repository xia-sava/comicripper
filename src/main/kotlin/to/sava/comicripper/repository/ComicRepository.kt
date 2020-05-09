package to.sava.comicripper.repository

import to.sava.comicripper.model.Comic

class ComicRepository {
    suspend fun exampleListComic(): List<Comic> {
        return listOf("coverF.jpg", "coverF_001.jpg", "coverF_002.jpg").map {
            Comic().apply {
                coverAll = "file:////c:/tmp/C/$it"
            }
        }
    }
}