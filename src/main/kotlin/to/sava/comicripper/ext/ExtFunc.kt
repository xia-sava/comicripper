package to.sava.comicripper.ext

import to.sava.comicripper.model.Setting

object Loader

fun workFilename(filename: String): String {
    return "${Setting.workDirectory}/$filename"
}
