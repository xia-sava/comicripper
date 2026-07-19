package to.sava.comicripper.ext

object Loader

fun workFilename(filename: String, workDirectory: String): String {
    return "${workDirectory}/$filename"
}
