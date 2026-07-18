package to.sava.comicripper.ext

import javafx.application.Platform
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.coroutines.CoroutineContext

/**
 * kotlinx-coroutines-javafx の Dispatchers.JavaFx 相当を自前で用意したもの．
 * kotlinx-coroutines-javafx への依存自体を無くし、Dispatchers.Main を
 * JavaFXに握らせない（Compose Desktop側に明け渡す）ために使う．
 */
object FxDispatcher : CoroutineDispatcher() {
    override fun dispatch(context: CoroutineContext, block: Runnable) {
        Platform.runLater(block)
    }
}
