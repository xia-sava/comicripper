package to.sava.comicripper.domain.service

interface FileWatcher {
    fun start(
        workDirectory: String,
        onFilesAdded: (filenames: List<String>) -> Unit,
        onFilesDeleted: (filenames: List<String>) -> Unit
    )

    fun stop()
}