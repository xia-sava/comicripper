package to.sava.comicripper.infrastructure.service

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import to.sava.comicripper.infrastructure.text.normalizeBookName
import to.sava.comicripper.model.Setting
import java.io.InputStreamReader
import java.net.URI

private val logger = KotlinLogging.logger {}

private const val FETCH_TIMEOUT_MS = 10_000

/** HTMLを取得する。テストからは固定の文書を返す実装へ差し替える。 */
fun interface HtmlFetcher {
    suspend fun fetch(url: String): Document
}

/** テキストを取得する。テストからは固定の応答を返す実装へ差し替える。 */
fun interface TextFetcher {
    suspend fun fetch(url: String): String
}

/** Jsoup でページを取得する既定の実装。 */
class JsoupHtmlFetcher : HtmlFetcher {
    override suspend fun fetch(url: String): Document =
        withContext(Dispatchers.IO) { Jsoup.connect(url).timeout(FETCH_TIMEOUT_MS).get() }
}

/** URL の内容をそのまま読む既定の実装。 */
class UrlTextFetcher : TextFetcher {
    override suspend fun fetch(url: String): String =
        withContext(Dispatchers.IO) {
            InputStreamReader(URI(url).toURL().openConnection().getInputStream(), "utf-8").buffered().readText()
        }
}

/**
 * ISBN から著者名・題名を引く。
 *
 * Amazon → ヨドバシ → Google Books の順に試し、ある提供元での失敗（通信不能・タイムアウト・
 * 想定外の応答）は次の提供元へ進むために握る。どこからも引けなければ ISBN そのものを題名として返す。
 */
class BookInfoSearcher(
    private val setting: Setting,
    private val htmlFetcher: HtmlFetcher = JsoupHtmlFetcher(),
    private val textFetcher: TextFetcher = UrlTextFetcher(),
) {
    suspend fun search(isbn10or13: String): Pair<String, String> {
        val isbn = if (isbn10or13.length == 13) isbn10or13 else "978$isbn10or13"
        return searchAmazon(isbn)
            ?: searchYodobashi(isbn)
            ?: searchGoogleBooks(isbn)
            ?: Pair("ISBN", isbn)
    }

    private suspend fun searchAmazon(isbn: String): Pair<String, String>? = fromProvider("Amazon", isbn) {
        htmlFetcher.fetch("https://www.amazon.co.jp/s?k=isbn+$isbn")
            .select("#search .s-main-slot a[href]").firstOrNull()
            ?.absUrl("href")
            ?.let { htmlFetcher.fetch(it) }
            ?.let { page ->
                val title = page.select("#productTitle").first()?.text() ?: return@let null
                val authors = page.select("#bylineInfo .author a")
                    .map { it.text() }
                    .filter { it != "" }
                    .filter { t -> listOf("原著", "著者ページ", "検索結果").all { it !in t } }
                    .ifEmpty {
                        page.select("#bylineInfo .author a")
                            .map { it.text() }
                            .ifEmpty { listOf("作者不明") }
                    }
                normalizeBookName(authors, title)
            }
    }

    private suspend fun searchYodobashi(isbn: String): Pair<String, String>? = fromProvider("Yodobashi", isbn) {
        htmlFetcher.fetch("${setting.yodobashiSearchUrl}$isbn")
            .takeIf { it.select(".noResult").isEmpty() }
            ?.select(".pListBlock a[href]")?.firstOrNull()
            ?.absUrl("href")
            ?.let { htmlFetcher.fetch(it) }
            ?.let { page ->
                val title = page.select("#products_maintitle").first()?.text() ?: return@let null
                val authors = page.select("#js_bookAuthor a")
                    .map { it.text() }
                    .ifEmpty { listOf("作者不明") }
                normalizeBookName(authors, title)
            }
    }

    private suspend fun searchGoogleBooks(isbn: String): Pair<String, String>? = fromProvider("Google", isbn) {
        val json = Json.parseToJsonElement(textFetcher.fetch("${setting.googleBookApi}$isbn")).jsonObject
        if ((json["totalItems"]?.jsonPrimitive?.intOrNull ?: 0) <= 0) {
            return@fromProvider null
        }
        val info = json["items"]?.jsonArray?.getOrNull(0)?.jsonObject?.get("volumeInfo")?.jsonObject
        val authors = info?.get("authors")?.jsonArray?.map { it.jsonPrimitive.content } ?: return@fromProvider null
        val title = info["title"]?.jsonPrimitive?.contentOrNull ?: return@fromProvider null
        normalizeBookName(authors, title)
    }

    /**
     * 1つの提供元へ問い合わせる。失敗は次の提供元へ進むために握るので、取り消しだけは通す。
     */
    private suspend fun fromProvider(
        name: String,
        isbn: String,
        block: suspend () -> Pair<String, String>?,
    ): Pair<String, String>? =
        try {
            logger.info { "$name $isbn start" }
            block()?.also { logger.info { "$name $isbn done" } }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.warn(e) { "$name $isbn error" }
            null
        }
}
