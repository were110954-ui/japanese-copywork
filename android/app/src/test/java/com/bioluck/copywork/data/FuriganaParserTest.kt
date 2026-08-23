package com.bioluck.copywork.data

import org.junit.Assert.*
import org.junit.Test

class FuriganaParserTest {
    @Test fun wrapsOnlyKanjiAndLeavesOkuriganaPlain() {
        val html = rubyOnlyKanji("食べる", "たべる")
        assertTrue(html.contains(">食<rt>た</rt></ruby>べる"))
        assertFalse(html.contains("<ruby>べ"))
    }

    @Test fun mapsMultipleKanjiRunsAroundKana() {
        val html = rubyOnlyKanji("取り戻す", "とりもどす")
        assertTrue(html.contains(">取<rt>と</rt></ruby>り"))
        assertTrue(html.contains(">戻<rt>もど</rt></ruby>す"))
    }

    @Test fun neverWrapsKanaLatinNumbersOrPunctuation() {
        listOf("ひらがな", "カタカナ", "ABC123", "。、・").forEach { value ->
            assertEquals(value, rubyOnlyKanji(value, value))
        }
    }

    @Test fun nativeSegmentsMakeOnlyKanjiClickable() {
        val segments = JapaneseTextParser().parse("食べる。かなABC").flatten()
        val kanji = segments.first { it.text == "食" }
        assertEquals("た", kanji.reading)
        assertEquals("食べる", kanji.lookupWord)
        assertTrue(segments.filter { it.text != "食" }.all { it.lookupWord == null })
    }

    @Test fun mapsPassiveVerbReadingToKanjiStem() {
        val segments = JapaneseTextParser().parse("刺される").flatten()
        val kanji = segments.first { it.text == "刺" }
        assertEquals(segments.toString(), "さ", kanji.reading)
    }

    @Test fun recognizesParenthesizedRubyWithoutDisplayingParentheses() {
        val segments = JapaneseTextParser().parse("蚊に刺(さ)される").flatten()
        assertTrue(segments.none { '(' in it.text || ')' in it.text })
        assertEquals("さ", segments.first { it.text == "刺" }.reading)
    }
}
