package com.bioluck.copywork.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.bioluck.copywork.CopyworkApp
import com.bioluck.copywork.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.*

enum class ProgressFilter { INCOMPLETE, COMPLETE }
data class HomeState(
    val filter: ProgressFilter = ProgressFilter.INCOMPLETE,
    val articles: List<ArticleEntity> = emptyList(), val incompleteCount: Int = 0, val completeCount: Int = 0,
    val loading: Boolean = false, val message: String? = null,
    val vocabulary: List<VocabularyEntity> = emptyList(), val totalCompleted: Int = 0, val streakDays: Int = 0,
    val completionTrend: List<Int> = List(7) { 0 }, val sync: SyncProgress = SyncProgress(),
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as CopyworkApp).repository
    private val filter = MutableStateFlow(ProgressFilter.INCOMPLETE)
    private val message = MutableStateFlow<String?>(null)

    private val base = combine(filter, repository.articleStates(), repository.vocabulary(), repository.syncProgress, message) { values ->
        @Suppress("UNCHECKED_CAST")
        Base(values[0] as ProgressFilter, values[1] as List<ArticleStateEntity>,
            values[2] as List<VocabularyEntity>, values[3] as SyncProgress, values[4] as String?)
    }
    val state: StateFlow<HomeState> = base.flatMapLatest { base -> repository.articles("tenseijingo").map { all ->
        val completed = base.states.filter { it.completed }.map { it.articleId }.toSet()
        HomeState(base.filter,
            all.filter { (it.id in completed) == (base.filter == ProgressFilter.COMPLETE) },
            all.count { it.id !in completed }, all.count { it.id in completed }, base.sync.running, base.message,
            base.words, completed.size, calculateStreak(base.states), completionTrend(base.states), base.sync)
    } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    init { viewModelScope.launch { runCatching { repository.ensureInitialData() }.onFailure { message.value = "동기화에 실패해 저장된 글을 표시합니다." } } }
    fun selectFilter(value: ProgressFilter) { filter.value = value }
    fun deleteWord(word: String) = viewModelScope.launch { repository.deleteVocabulary(word) }
    fun refresh(fullSync: Boolean = false) = viewModelScope.launch {
        runCatching { repository.sync(incremental = !fullSync) }.onSuccess { message.value = null }
            .onFailure { message.value = "동기화에 실패해 저장된 글을 표시합니다." }
    }
    private data class Base(val filter: ProgressFilter, val states: List<ArticleStateEntity>, val words: List<VocabularyEntity>, val sync: SyncProgress, val message: String?)
    private fun calculateStreak(states: List<ArticleStateEntity>): Int {
        val days = states.mapNotNull { it.completedAt }.map { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }.toSet()
        var cursor = LocalDate.now(); if (cursor !in days) cursor = cursor.minusDays(1)
        var count = 0; while (cursor in days) { count++; cursor = cursor.minusDays(1) }; return count
    }
    private fun completionTrend(states: List<ArticleStateEntity>): List<Int> {
        val today = LocalDate.now()
        val completedDays = states.mapNotNull { it.completedAt }
            .map { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate() }
        return (6 downTo 0).map { offset -> completedDays.count { it == today.minusDays(offset.toLong()) } }
    }
}
