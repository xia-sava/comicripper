package to.sava.comicripper.infrastructure.service

import kotlinx.coroutines.test.runTest
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import to.sava.comicripper.model.Setting
import java.io.IOException
import java.net.SocketTimeoutException

class BookInfoSearcherTest {

    private lateinit var setting: Setting

    @BeforeEach
    fun setup() {
        setting = Setting()
    }

    private fun document(html: String, baseUri: String = "https://example.com/") = Jsoup.parse(html, baseUri)

    /** URLごとに返す文書を決める取得口。どのURLが引かれたかも記録する。 */
    private class StubHtmlFetcher(private val pages: Map<String, Document>) : HtmlFetcher {
        val requested = mutableListOf<String>()

        override suspend fun fetch(url: String): Document {
            requested += url
            return pages[url] ?: throw IOException("404: $url")
        }
    }

    private fun amazonSearchPage(detailUrl: String) = Jsoup.parse(
        """<div id="search"><div class="s-main-slot"><a href="$detailUrl">商品</a></div></div>""",
        "https://www.amazon.co.jp/",
    )

    private fun amazonDetailPage(title: String, vararg authors: String) = Jsoup.parse(
        """
        <span id="productTitle">$title</span>
        <div id="bylineInfo">${authors.joinToString("") { """<span class="author"><a>$it</a></span>""" }}</div>
        """.trimIndent(),
    )

    private fun yodobashiSearchPage(detailUrl: String) = Jsoup.parse(
        """<div class="pListBlock"><a href="$detailUrl">商品</a></div>""",
        "https://www.yodobashi.com/",
    )

    private fun yodobashiDetailPage(title: String, vararg authors: String) = Jsoup.parse(
        """
        <h1 id="products_maintitle">$title</h1>
        <div id="js_bookAuthor">${authors.joinToString("") { "<a>$it</a>" }}</div>
        """.trimIndent(),
    )

    private fun googleBooksJson(title: String, vararg authors: String) = """
        {"totalItems": 1, "items": [{"volumeInfo": {
            "title": "$title",
            "authors": [${authors.joinToString(", ") { "\"$it\"" }}]
        }}]}
    """.trimIndent()

    @Nested
    inner class `提供元の優先順` {

        @Test
        fun `Amazonで見つかればそこで確定する`() = runTest {
            val detailUrl = "https://www.amazon.co.jp/dp/1"
            val fetcher = StubHtmlFetcher(
                mapOf(
                    "https://www.amazon.co.jp/s?k=isbn+9781234567897" to amazonSearchPage(detailUrl),
                    detailUrl to amazonDetailPage("タイトル(1)", "著者A"),
                )
            )
            val searcher = BookInfoSearcher(setting, fetcher) { error("Googleは引かれないはず") }

            val (author, title) = searcher.search("9781234567897")

            assertEquals("著者A", author)
            assertEquals("タイトル (1)", title)
        }

        @Test
        fun `Amazonが見つからなければヨドバシへ進む`() = runTest {
            val detailUrl = "https://www.yodobashi.com/product/1"
            val fetcher = StubHtmlFetcher(
                mapOf(
                    "${setting.yodobashiSearchUrl}9781234567897" to yodobashiSearchPage(detailUrl),
                    detailUrl to yodobashiDetailPage("タイトル 第2巻", "著者B"),
                )
            )
            val searcher = BookInfoSearcher(setting, fetcher) { error("Googleは引かれないはず") }

            val (author, title) = searcher.search("9781234567897")

            assertEquals("著者B", author)
            assertEquals("タイトル (2)", title)
        }

        @Test
        fun `スクレイピングが両方だめならGoogle Booksへ進む`() = runTest {
            val searcher = BookInfoSearcher(
                setting,
                StubHtmlFetcher(emptyMap()),
            ) { googleBooksJson("タイトル[3]", "著者C") }

            val (author, title) = searcher.search("9781234567897")

            assertEquals("著者C", author)
            assertEquals("タイトル (3)", title)
        }

        @Test
        fun `どこからも引けなければISBNを題名として返す`() = runTest {
            val searcher = BookInfoSearcher(setting, StubHtmlFetcher(emptyMap())) { """{"totalItems": 0}""" }

            val (author, title) = searcher.search("9781234567897")

            assertEquals("ISBN", author)
            assertEquals("9781234567897", title)
        }
    }

    @Nested
    inner class `失敗しても次の提供元へ進む` {

        @Test
        fun `タイムアウトでも次の提供元を試す`() {
            val detailUrl = "https://www.yodobashi.com/product/1"
            val pages = mapOf(
                "${setting.yodobashiSearchUrl}9781234567897" to yodobashiSearchPage(detailUrl),
                detailUrl to yodobashiDetailPage("タイトル", "著者B"),
            )
            val fetcher = object : HtmlFetcher {
                override suspend fun fetch(url: String): Document {
                    if (url.contains("amazon")) {
                        throw SocketTimeoutException("timeout")
                    }
                    return pages[url] ?: throw IOException("404: $url")
                }
            }
            val searcher = BookInfoSearcher(setting, fetcher) { error("Googleは引かれないはず") }

            runTest {
                val (author, _) = searcher.search("9781234567897")

                assertEquals("著者B", author)
            }
        }

        @Test
        fun `Google Booksの応答が壊れていてもISBNを返して終わる`() = runTest {
            val searcher = BookInfoSearcher(setting, StubHtmlFetcher(emptyMap())) { "これはJSONではない" }

            val (author, title) = searcher.search("9781234567897")

            assertEquals("ISBN", author)
            assertEquals("9781234567897", title)
        }
    }

    @Nested
    inner class `ISBNの桁合わせ` {

        @Test
        fun `10桁のISBNは978を前置して引く`() = runTest {
            val fetcher = StubHtmlFetcher(emptyMap())
            val searcher = BookInfoSearcher(setting, fetcher) { """{"totalItems": 0}""" }

            val (_, title) = searcher.search("1234567897")

            assertEquals("9781234567897", title)
            assertEquals("https://www.amazon.co.jp/s?k=isbn+9781234567897", fetcher.requested.first())
        }

        @Test
        fun `13桁のISBNはそのまま引く`() = runTest {
            val fetcher = StubHtmlFetcher(emptyMap())
            val searcher = BookInfoSearcher(setting, fetcher) { """{"totalItems": 0}""" }

            searcher.search("9781234567897")

            assertEquals("https://www.amazon.co.jp/s?k=isbn+9781234567897", fetcher.requested.first())
        }
    }

    @Test
    fun `著者が取れないページでは作者不明とする`() = runTest {
        val detailUrl = "https://www.amazon.co.jp/dp/1"
        val fetcher = StubHtmlFetcher(
            mapOf(
                "https://www.amazon.co.jp/s?k=isbn+9781234567897" to amazonSearchPage(detailUrl),
                detailUrl to document("""<span id="productTitle">タイトル</span><div id="bylineInfo"></div>"""),
            )
        )
        val searcher = BookInfoSearcher(setting, fetcher) { error("Googleは引かれないはず") }

        val (author, _) = searcher.search("9781234567897")

        assertEquals("作者不明", author)
    }
}
