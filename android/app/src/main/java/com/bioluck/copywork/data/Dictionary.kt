package com.bioluck.copywork.data

import com.atilika.kuromoji.ipadic.Tokenizer

data class DictionaryDto(
    val word: String, val reading: String, val meaning: String,
    val partOfSpeech: String = "", val onyomi: String = "", val kunyomi: String = "",
    val romanization: String = "",
)

data class AdvancedVocabulary(val entry: DictionaryDto, val level: String)

class OfflineDictionary {
    private val tokenizer by lazy { Tokenizer() }
    private val entries = mapOf(
        "猛" to DictionaryDto("猛", "もう", "사납다, 맹렬하다", "한자", "モウ", "たけ-し", "mō"),
        "猛暑" to DictionaryDto("猛暑", "もうしょ", "맹렬한 더위, 폭염", "명사", "モウ・ショ", "たけ-し・あつ-い", "mōsho"),
        "春" to DictionaryDto("春", "はる・しゅん", "봄", "한자", "シュン", "はる"),
        "秋" to DictionaryDto("秋", "あき・しゅう", "가을", "한자", "シュウ", "あき"),
        "天" to DictionaryDto("天", "あめ・てん", "하늘", "한자", "テン", "あめ・あま"),
        "声" to DictionaryDto("声", "こえ・せい", "소리, 목소리", "한자", "セイ・ショウ", "こえ"),
        "人" to DictionaryDto("人", "ひと・じん・にん", "사람", "한자", "ジン・ニン", "ひと"),
        "語" to DictionaryDto("語", "ご・かたる", "말, 이야기하다", "한자", "ゴ", "かた-る"),
        "蚊" to DictionaryDto("蚊", "か", "모기; 모기 문", "한자", "ブン", "か", "ka"),
        "刺" to DictionaryDto("刺", "さす", "찌르다, 쏘다; 찌를 자", "한자", "シ", "さ-す・さ-さる", "sasu"),
        "刺される" to DictionaryDto("刺される", "さされる", "쏘이다, 찔리다", "동사", "シ", "さ-される", "sasareru"),
        "無比" to DictionaryDto("無比", "むひ", "비할 데 없음, 비할 바가 없음", "명사·형용동사", "ム・ヒ", "", "muhi"),
        "不妊化" to DictionaryDto("不妊化", "ふにんか", "불임화", "명사", "フ・ニン・カ", "", "funinka"),
        "媒介" to DictionaryDto("媒介", "ばいかい", "매개, 중개", "명사·サ변동사", "バイ・カイ", "", "baikai"),
        "顕著" to DictionaryDto("顕著", "けんちょ", "현저함, 두드러짐", "형용동사", "ケン・チョ", "", "kencho"),
        "余儀なく" to DictionaryDto("余儀なく", "よぎなく", "어쩔 수 없이, 부득이하게", "관용구", "ヨ・ギ", "", "yoginaku"),
        "懸念" to DictionaryDto("懸念", "けねん", "염려, 우려", "명사·サ변동사", "ケン・ネン", "", "kenen"),
        "促進" to DictionaryDto("促進", "そくしん", "촉진", "명사·サ변동사", "ソク・シン", "", "sokushin"),
        "阻害" to DictionaryDto("阻害", "そがい", "저해, 방해", "명사·サ변동사", "ソ・ガイ", "", "sogai"),
    )
    private val advanced = listOf(
        AdvancedVocabulary(entries.getValue("無比"), "N1"),
        AdvancedVocabulary(entries.getValue("不妊化"), "N1"),
        AdvancedVocabulary(entries.getValue("媒介"), "N1"),
        AdvancedVocabulary(entries.getValue("顕著"), "N2"),
        AdvancedVocabulary(entries.getValue("余儀なく"), "N1"),
        AdvancedVocabulary(entries.getValue("懸念"), "N2"),
        AdvancedVocabulary(entries.getValue("促進"), "N2"),
        AdvancedVocabulary(entries.getValue("阻害"), "N1"),
    )

    fun lookup(word: String): DictionaryDto {
        entries[word]?.let { return it }
        val tokens = tokenizer.tokenize(word)
        val reading = tokens.joinToString("") { it.reading.takeUnless { value -> value == "*" } ?: it.surface }
        val part = tokens.firstOrNull()?.partOfSpeechLevel1.orEmpty()
        val components = word.filter { it.code in 0x4E00..0x9FAF }.mapNotNull { entries[it.toString()]?.meaning }.distinct()
        val meaning = if (components.isNotEmpty()) components.joinToString(" · ")
        else "‘$word’ — 문맥에 따라 의미를 확인해야 하는 일본어 ${if (word.length == 1) "한자" else "어휘"}"
        return DictionaryDto(word, katakanaToHiragana(reading), meaning, part.ifBlank { "일본어 어휘" })
    }

    fun extractAdvanced(text: String): List<AdvancedVocabulary> = advanced.filter { it.entry.word in text }
}

internal fun katakanaToHiragana(value: String): String = value.map { ch ->
    if (ch.code in 0x30A1..0x30F6) (ch.code - 0x60).toChar() else ch
}.joinToString("")
