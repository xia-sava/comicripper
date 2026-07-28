package to.sava.comicripper.infrastructure.text

import java.text.Normalizer

/** ファイル名・アーカイブ名に使えない半角記号を対応する全角文字へ置き換えるための変換表。 */
private val FULLWIDTH_CHAR_MAP: Map<Char, Char> = mapOf(
    Character.codePointOf("FULLWIDTH TILDE").toChar() to '～',
    Character.codePointOf("WAVE DASH").toChar() to '～',
    '!' to '！',
    '\'' to '’',
    '"' to '”',
    '%' to '％',
    '&' to '＆',
    ':' to '：',
    '*' to '＊',
    '?' to '？',
    '<' to '＜',
    '>' to '＞',
    '|' to '｜',
    '~' to '～',
    '/' to '／',
    '\\' to '￥',
)

/** タイトル中の各種括弧を `<` `>` へ統一するための変換表。 */
private val BRACKET_CHAR_MAP: Map<Char, Char> = mapOf(
    '(' to '<', ')' to '>',
    '[' to '<', ']' to '>',
    '{' to '<', '}' to '>',
    '＜' to '<', '＞' to '>',
    '「' to '<', '」' to '>',
    '〔' to '<', '〕' to '>',
    '【' to '<', '】' to '>',
    '『' to '<', '』' to '>',
    '《' to '<', '》' to '>',
)

/**
 * 表記の揺れをまとめ、ファイル名に使えない文字を全角へ置き換える。
 * NFKC で全角英数などを半角へ寄せてから、禁止文字だけを全角へ戻す。
 */
internal fun normalizeText(text: String): String {
    val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC)
    return FULLWIDTH_CHAR_MAP.entries.fold(normalized) { acc, (from, to) -> acc.replace(from, to) }
}

/**
 * 書誌情報を保存名の形へ整える。著者名は `／` で連結し、題名の巻数表記は `(1)` へ統一する。
 */
internal fun normalizeBookName(authors: Iterable<String>, title: String): Pair<String, String> {
    val a = authors.joinToString("／") {
        normalizeText(it).replace(" ", "")
    }
    val bracketsUnified = BRACKET_CHAR_MAP.entries.fold(normalizeText(title)) { acc, (from, to) -> acc.replace(from, to) }
    val t = bracketsUnified
        .replace("""<.*?(\d*).*?>""".toRegex(), "<$1>")
        .replace("""\s+第?\s*(\d+)\s*巻""".toRegex(), " <$1>")
        .replace("""：\s*(\d+)""".toRegex(), " <$1>")
        .replace("<>", "")
        .trimEnd()
        .replace("""\s*<?(\d+)>?[\d<> ]*$""".toRegex(), " ($1)")
    return Pair(a, t)
}
