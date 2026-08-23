package com.bioluck.copywork.data

import com.atilika.kuromoji.ipadic.Tokenizer

data class InteractiveSegment(
    val text: String,
    val reading: String = "",
    val lookupWord: String? = null,
)

class JapaneseTextParser {
    private val tokenizer by lazy { Tokenizer() }
    private val kanji = Regex("[\\u4E00-\\u9FAF]+")
    private val explicitRuby = Regex("([\\u4E00-\\u9FAF]+)[（(]([\\u3041-\\u309F]+)[）)]")

    fun parse(text: String): List<List<InteractiveSegment>> = text.lineSequence()
        .filter { it.isNotBlank() }
        .map(::parseParagraph)
        .toList()

    private fun parseParagraph(paragraph: String): List<InteractiveSegment> {
        val result = mutableListOf<InteractiveSegment>(); var cursor = 0
        explicitRuby.findAll(paragraph).forEach { match ->
            if (match.range.first > cursor) result += tokenizer.tokenize(paragraph.substring(cursor, match.range.first)).flatMap(::segmentsForToken)
            result += InteractiveSegment(match.groupValues[1], match.groupValues[2], match.groupValues[1])
            cursor = match.range.last + 1
        }
        if (cursor < paragraph.length) result += tokenizer.tokenize(paragraph.substring(cursor)).flatMap(::segmentsForToken)
        return result
    }

    private fun segmentsForToken(token: com.atilika.kuromoji.ipadic.Token): List<InteractiveSegment> {
        val surface = token.surface
        val reading = token.reading.takeUnless { it == "*" }?.let(::katakanaToHiragana)
            .orEmpty().ifBlank { fallbackKanjiReading(surface) }
        if (reading.isBlank() || !kanji.containsMatchIn(surface)) return listOf(InteractiveSegment(surface))
        val result = mutableListOf<InteractiveSegment>(); var surfaceCursor = 0; var readingCursor = 0
        val matches = kanji.findAll(surface).toList()
        matches.forEachIndexed { index, match ->
            val plainBefore = surface.substring(surfaceCursor, match.range.first)
            if (plainBefore.isNotEmpty()) {
                result += InteractiveSegment(plainBefore)
                if (reading.startsWith(plainBefore, readingCursor)) readingCursor += plainBefore.length
            }
            val nextStart = matches.getOrNull(index + 1)?.range?.first ?: surface.length
            val plainAfter = surface.substring(match.range.last + 1, nextStart)
            // 刺さ(ささ)처럼 한자 읽기와 오쿠리가나가 같은 음으로 시작하면 첫 음이 아니라 다음 일치를 경계로 쓴다.
            val separator = if (plainAfter.isNotEmpty()) reading.indexOf(plainAfter, readingCursor + 1) else -1
            val rubyReading = when {
                separator >= readingCursor -> reading.substring(readingCursor, separator)
                index == matches.lastIndex && plainAfter.isNotEmpty() && reading.endsWith(plainAfter) -> reading.substring(readingCursor, reading.length - plainAfter.length)
                index == matches.lastIndex -> reading.substring(readingCursor)
                else -> ""
            }
            result += InteractiveSegment(match.value, rubyReading, surface)
            readingCursor += rubyReading.length; surfaceCursor = match.range.last + 1
        }
        surface.substring(surfaceCursor).takeIf { it.isNotEmpty() }?.let { result += InteractiveSegment(it) }
        return result
    }
}

/** Kuromoji가 활용형 어간을 미등록 단일 토큰(*)으로 반환하는 경우의 상용 어간 읽기. */
internal fun fallbackKanjiReading(surface: String): String = mapOf(
    "刺" to "さ", "行" to "い", "来" to "く", "見" to "み", "聞" to "き",
    "読" to "よ", "書" to "か", "取" to "と", "持" to "も", "立" to "た",
    "使" to "つか", "考" to "かんが", "求" to "もと", "続" to "つづ", "働" to "はたら",
)[surface].orEmpty()
