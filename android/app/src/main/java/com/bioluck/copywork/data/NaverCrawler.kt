package com.bioluck.copywork.data

import com.atilika.kuromoji.ipadic.Tokenizer
import com.google.gson.JsonParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.net.URLDecoder
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.random.Random

data class SyncProgress(val running: Boolean = false, val current: Int = 0, val total: Int = 0, val message: String = "")
private data class PostMeta(val logNo: String, val title: String, val date: String) {
    val url get() = "https://m.blog.naver.com/bioluck/$logNo"
}

class NaverCrawler(private val dao: CopyworkDao) {
    val progress = MutableStateFlow(SyncProgress())
    private val tokenizer by lazy { Tokenizer() }
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).build()
    private val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36",
        "Referer" to "https://m.blog.naver.com/",
        "Accept-Language" to "ko-KR,ko;q=0.9,ja;q=0.8,en-US;q=0.7",
    )
    private val requestMutex = Mutex()
    private var lastRequestAt = 0L

    suspend fun syncAll(incremental: Boolean): Int {
        progress.value = SyncProgress(true, message = "전체 기사 목록 확인 중…")
        return try {
            val metas = loadAllPostMetas(incremental)
            val targets = if (incremental) metas.filter { dao.findBySourceUrl(it.url) == null } else metas
            progress.value = SyncProgress(true, 0, targets.size, if (targets.isEmpty()) "최신 상태입니다." else "본문 수집 준비 중…")
            var saved = 0
            targets.forEach { meta ->
                try {
                    val article = parsePost(meta)
                    dao.upsertArticleBySource(article)
                    saved++
                    progress.value = SyncProgress(true, saved, targets.size, "기사 수집 중… (${saved}번째 기사 저장 완료)")
                } catch (error: HttpStatusException) {
                    // 429 재시도를 모두 소진했다면 성공처럼 넘기지 말고 WorkManager가 나중에 재시도하게 한다.
                    if (error.status == 429) throw error
                }
            }
            progress.value = SyncProgress(false, saved, targets.size, "${saved}개 기사 동기화 완료")
            saved
        } catch (error: Exception) {
            progress.value = SyncProgress(false, message = "동기화 실패: ${error.message ?: "네트워크 오류"}")
            throw error
        }
    }

    suspend fun rebuildLegacyRuby() {
        dao.legacyRubyArticles().forEach { article ->
            val normalized = article.japaneseText.lineSequence().filter { it.isNotBlank() }
                .joinToString("") { "<p>${furiganaHtml(it)}</p>" }
            dao.updateRubyHtml(article.id, normalized)
        }
    }

    private suspend fun loadAllPostMetas(incremental: Boolean): List<PostMeta> {
        val result = linkedMapOf<String, PostMeta>()
        pageLoop@ for (page in 1..1000) {
            val url = "https://blog.naver.com/PostTitleListAsync.naver?blogId=bioluck&viewdate=&currentPage=$page&categoryNo=&parentCategoryNo=&countPerPage=30"
            val json = get(url)
            val posts = JsonParser.parseString(json).asJsonObject.getAsJsonArray("postList") ?: break
            if (posts.size() == 0) break
            val pageIds = posts.mapNotNull { it.asJsonObject.get("logNo")?.asString }
            if (pageIds.isNotEmpty() && pageIds.all { it in result }) break
            for (item in posts) {
                val obj = item.asJsonObject
                val logNo = obj.get("logNo")?.asString ?: continue
                val title = URLDecoder.decode(obj.get("title")?.asString.orEmpty(), "UTF-8").replace('+', ' ')
                val meta = PostMeta(logNo, title, obj.get("addDate")?.asString.orEmpty())
                if (category(title) != null) {
                    // 목록은 최신순이다. 첫 중복을 만나는 순간 그 뒤는 이미 저장된 과거 글이므로 즉시 종료한다.
                    if (incremental && dao.findBySourceUrl(meta.url) != null) break@pageLoop
                    result[logNo] = meta
                }
            }
            progress.value = SyncProgress(true, result.size, 0, "${result.size}개 기사 목록 확인 중…")
        }
        return result.values.toList()
    }

    private suspend fun parsePost(meta: PostMeta): ArticleEntity {
        val document = Jsoup.parse(get(meta.url), meta.url, Parser.htmlParser())
        val root = document.selectFirst(".se-main-container, .post-view, #postViewArea") ?: document.body()
        val title = document.selectFirst("meta[property=og:title]")?.attr("content")?.takeIf { it.isNotBlank() } ?: meta.title
        val category = category(title + " " + root.text().take(250)) ?: error("사설 카테고리가 아닙니다")
        val lines = root.select(".se-text-paragraph").map { it.text().trim() }.filter { it.isNotBlank() }
            .ifEmpty { root.wholeText().lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList() }
            .fold(mutableListOf<String>()) { acc, line -> if (acc.lastOrNull() != line) acc += line; acc }
        val (japanese, korean) = splitLanguages(lines)
        require(japanese.isNotEmpty()) { "일본어 원문을 찾지 못했습니다" }
        // Normalize every paragraph so even publisher-provided ruby cannot include okurigana.
        val rubyHtml = japanese.joinToString("") { "<p>${furiganaHtml(it)}</p>" }
        val thumbnail = document.selectFirst("meta[property=og:image]")?.attr("content").orEmpty()
            .ifBlank { root.selectFirst("img[src], img[data-lazy-src], img[data-src]")?.let { it.attr("data-lazy-src").ifBlank { it.attr("data-src") }.ifBlank { it.absUrl("src") } }.orEmpty() }
        val published = Regex("(20\\d{2})[.\\-/년\\s]+(\\d{1,2})[.\\-/월\\s]+(\\d{1,2})").find(title + " " + meta.date)?.groupValues?.drop(1)?.joinToString("-") { it.padStart(2, '0') } ?: LocalDate.now().toString()
        return ArticleEntity(meta.logNo.toLong(), meta.url, category, title, published, rubyHtml, japanese.joinToString("\n"), korean.joinToString("\n"), thumbnail)
    }

    private fun splitLanguages(lines: List<String>): Pair<List<String>, List<String>> {
        val ja = mutableListOf<String>(); val ko = mutableListOf<String>()
        for (raw in lines) {
            if (raw.startsWith("http://") || raw.startsWith("https://")) break
            val line = raw.removePrefix("▼").trim()
            val kana = line.count { it.code in 0x3041..0x30FA }; val hangul = line.count { it.code in 0xAC00..0xD7A3 }
            if (kana >= 3 && kana > hangul) ja += line else if (hangul >= 3 && hangul > kana) ko += line
        }
        return ja to ko
    }

    private fun furiganaHtml(text: String): String = tokenizer.tokenize(text).joinToString("") { token ->
        val reading = token.reading.takeUnless { it == "*" }?.let(::katakanaToHiragana)
            .orEmpty().ifBlank { fallbackKanjiReading(token.surface) }
        rubyOnlyKanji(token.surface, reading)
    }

    private fun category(value: String): String? =
        if (Regex("天声人語|천성인어", RegexOption.IGNORE_CASE).containsMatchIn(value)) "tenseijingo" else null

    /** 모든 목록/본문 요청을 직렬화하고 요청 시작 사이에 500~1000ms 간격을 보장한다. */
    private suspend fun get(url: String): String = requestMutex.withLock {
        val backoffMillis = longArrayOf(2_000L, 4_000L, 8_000L)
        repeat(backoffMillis.size + 1) { attempt ->
            throttle()
            val builder = Request.Builder().url(url)
            headers.forEach { (key, value) -> builder.header(key, value) }
            client.newCall(builder.build()).execute().use { response ->
                if (response.code == 429) {
                    if (attempt == backoffMillis.size) throw HttpStatusException(429, "HTTP 429: 잠시 후 다시 시도합니다.")
                    delay(backoffMillis[attempt])
                    return@use
                }
                if (!response.isSuccessful) throw HttpStatusException(response.code, "HTTP ${response.code}")
                return@withLock response.body?.string() ?: error("빈 응답")
            }
        }
        error("HTTP 요청 재시도 실패")
    }

    private suspend fun throttle() {
        val now = System.currentTimeMillis()
        val minimumInterval = Random.nextLong(500L, 1_001L)
        val remaining = minimumInterval - (now - lastRequestAt)
        if (remaining > 0) delay(remaining)
        lastRequestAt = System.currentTimeMillis()
    }
}

private class HttpStatusException(val status: Int, message: String) : java.io.IOException(message)

private val KANJI_RUN = Regex("[\\u4E00-\\u9FAF]+")

/** Wraps only kanji runs; okurigana, kana, Latin text, numbers and punctuation stay plain. */
internal fun rubyOnlyKanji(surface: String, reading: String): String {
    if (reading.isBlank() || !KANJI_RUN.containsMatchIn(surface)) return htmlEscape(surface)
    val result = StringBuilder(); var surfaceCursor = 0; var readingCursor = 0
    val matches = KANJI_RUN.findAll(surface).toList()
    matches.forEachIndexed { index, match ->
        val plainBefore = surface.substring(surfaceCursor, match.range.first)
        result.append(htmlEscape(plainBefore))
        if (plainBefore.isNotEmpty() && reading.startsWith(plainBefore, readingCursor)) readingCursor += plainBefore.length

        val nextKanjiStart = matches.getOrNull(index + 1)?.range?.first ?: surface.length
        val plainAfter = surface.substring(match.range.last + 1, nextKanjiStart)
        val separatorIndex = if (plainAfter.isNotEmpty()) reading.indexOf(plainAfter, readingCursor + 1) else -1
        val rubyReading = when {
            separatorIndex >= readingCursor -> reading.substring(readingCursor, separatorIndex)
            index == matches.lastIndex && plainAfter.isNotEmpty() && reading.endsWith(plainAfter) ->
                reading.substring(readingCursor, reading.length - plainAfter.length)
            index == matches.lastIndex -> reading.substring(readingCursor)
            else -> ""
        }
        val kanji = match.value
        if (rubyReading.isNotBlank()) result.append("<ruby class=\"kanji-token\" data-word=\"${htmlEscape(kanji)}\">${htmlEscape(kanji)}<rt>${htmlEscape(rubyReading)}</rt></ruby>")
        else result.append(htmlEscape(kanji))
        readingCursor += rubyReading.length
        surfaceCursor = match.range.last + 1
    }
    result.append(htmlEscape(surface.substring(surfaceCursor)))
    return result.toString()
}

private fun htmlEscape(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
