package com.bioluck.copywork.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class CopyworkRepository(private val dao: CopyworkDao) {
    private val crawler = NaverCrawler(dao)
    private val dictionary = OfflineDictionary()
    val syncProgress: StateFlow<SyncProgress> = crawler.progress

    fun articles(category: String): Flow<List<ArticleEntity>> = dao.observeArticles(category)
    fun article(id: Long): Flow<ArticleEntity?> = dao.observeArticle(id)
    fun draft(id: Long): Flow<DraftEntity?> = dao.observeDraft(id)
    fun articleState(id: Long): Flow<ArticleStateEntity?> = dao.observeArticleState(id)
    fun articleStates(): Flow<List<ArticleStateEntity>> = dao.observeArticleStates()
    fun vocabulary(): Flow<List<VocabularyEntity>> = dao.observeVocabulary()

    suspend fun sync(incremental: Boolean) = withContext(Dispatchers.IO) { crawler.syncAll(incremental) }
    suspend fun ensureInitialData() {
        withContext(Dispatchers.Default) { crawler.rebuildLegacyRuby() }
        if (dao.articleCount() == 0) sync(incremental = false) else sync(incremental = true)
    }
    suspend fun saveDraft(id: Long, text: String) = dao.upsertDraft(DraftEntity(id, text))
    suspend fun dictionary(word: String) = withContext(Dispatchers.Default) { dictionary.lookup(word) }
    suspend fun advancedVocabulary(text: String) = withContext(Dispatchers.Default) { dictionary.extractAdvanced(text) }
    suspend fun setCompleted(id: Long, current: ArticleStateEntity?, completed: Boolean) = dao.upsertArticleState((current ?: ArticleStateEntity(id)).copy(completed = completed, completedAt = if (completed) System.currentTimeMillis() else null))
    suspend fun setBookmarked(id: Long, current: ArticleStateEntity?, bookmarked: Boolean) = dao.upsertArticleState((current ?: ArticleStateEntity(id)).copy(bookmarked = bookmarked))
    suspend fun saveVocabulary(entry: DictionaryDto) = dao.upsertVocabulary(VocabularyEntity(entry.word, entry.reading, entry.meaning, entry.partOfSpeech, entry.onyomi, entry.kunyomi))
    suspend fun deleteVocabulary(word: String) = dao.deleteVocabulary(word)
}
