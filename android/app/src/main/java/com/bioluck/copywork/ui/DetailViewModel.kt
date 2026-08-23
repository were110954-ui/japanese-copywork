package com.bioluck.copywork.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bioluck.copywork.CopyworkApp
import com.bioluck.copywork.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DetailState(
    val article: ArticleEntity? = null,
    val paragraphs: List<List<InteractiveSegment>> = emptyList(),
    val translationVisible: Boolean = false,
    val dictionary: DictionaryDto? = null,
    val articleState: ArticleStateEntity? = null,
    val wordSaved: Boolean = false,
    val advancedVocabulary: List<AdvancedVocabulary> = emptyList(),
)

class DetailViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as CopyworkApp).repository
    private val articleId = MutableStateFlow<Long?>(null)
    private val dictionary = MutableStateFlow<DictionaryDto?>(null)
    private val wordSaved = MutableStateFlow(false)
    private val translationVisible = MutableStateFlow(false)
    private val parser = JapaneseTextParser()

    val state: StateFlow<DetailState> = articleId.filterNotNull().flatMapLatest { id ->
        val parsedArticle = repository.article(id).mapLatest { article ->
            Triple(article,
                article?.let { withContext(Dispatchers.Default) { parser.parse(it.japaneseText) } } ?: emptyList(),
                article?.let { repository.advancedVocabulary(it.japaneseText) } ?: emptyList())
        }
        combine(parsedArticle, repository.articleState(id), translationVisible, dictionary, wordSaved) { parsed, articleState, visible, entry, saved ->
            DetailState(parsed.first, parsed.second, visible, entry, articleState, saved, parsed.third)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailState())

    fun bind(id: Long) {
        if (articleId.value == id) return
        articleId.value = id
    }
    fun toggleTranslation() { translationVisible.value = !translationVisible.value }
    fun dismissDictionary() { dictionary.value = null; wordSaved.value = false }
    fun lookup(word: String) = viewModelScope.launch {
        dictionary.value = runCatching { repository.dictionary(word) }
            .getOrElse { DictionaryDto(word, "", "내장 사전에서 조회하지 못했습니다.") }
    }
    fun toggleBookmark() = viewModelScope.launch { articleId.value?.let { id ->
        repository.setBookmarked(id, state.value.articleState, state.value.articleState?.bookmarked != true)
    } }
    fun complete() = viewModelScope.launch { articleId.value?.let { id ->
        repository.setCompleted(id, state.value.articleState, true)
    } }
    fun reopen() = viewModelScope.launch { articleId.value?.let { id -> repository.setCompleted(id, state.value.articleState, false) } }
    fun saveWord() = viewModelScope.launch { dictionary.value?.let { repository.saveVocabulary(it); wordSaved.value = true } }
    fun saveWord(entry: DictionaryDto) = viewModelScope.launch { repository.saveVocabulary(entry) }
}
