package to.sava.comicripper

import javafx.application.Application
import javafx.stage.Stage

class Main: Application() {
    override fun start(primaryStage: Stage?) {
        checkNotNull(primaryStage)

        primaryStage.apply {
            width = 1280.0
            height = 720.0
        }
        primaryStage.show()
    }
}