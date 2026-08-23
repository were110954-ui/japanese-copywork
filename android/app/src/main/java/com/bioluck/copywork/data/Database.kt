package com.bioluck.copywork.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "articles", indices = [Index(value = ["sourceUrl"], unique = true)])
data class ArticleEntity(
    @PrimaryKey val id: Long,
    val sourceUrl: String,
    val category: String,
    val title: String,
    val publishedAt: String,
    val japaneseHtml: String,
    val japaneseText: String,
    val koreanText: String,
    val thumbnailUrl: String = "",
    val rubyVersion: Int = 2,
)

@Entity(tableName = "drafts")
data class DraftEntity(
    @PrimaryKey val articleId: Long,
    val text: String,
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "article_states")
data class ArticleStateEntity(
    @PrimaryKey val articleId: Long,
    val completed: Boolean = false,
    val bookmarked: Boolean = false,
    val completedAt: Long? = null,
)

@Entity(tableName = "vocabulary")
data class VocabularyEntity(
    @PrimaryKey val word: String,
    val reading: String,
    val meaning: String,
    val partOfSpeech: String = "",
    val onyomi: String = "",
    val kunyomi: String = "",
    val savedAt: Long = System.currentTimeMillis(),
)

@Dao
interface CopyworkDao {
    @Query("SELECT * FROM articles WHERE category = :category ORDER BY publishedAt DESC")
    fun observeArticles(category: String): Flow<List<ArticleEntity>>

    @Query("SELECT * FROM articles WHERE id = :id")
    fun observeArticle(id: Long): Flow<ArticleEntity?>

    @Query("SELECT * FROM articles WHERE sourceUrl = :sourceUrl LIMIT 1")
    suspend fun findBySourceUrl(sourceUrl: String): ArticleEntity?

    @Query("SELECT COUNT(*) FROM articles") suspend fun articleCount(): Int
    @Query("SELECT * FROM articles WHERE rubyVersion < 2") suspend fun legacyRubyArticles(): List<ArticleEntity>
    @Query("UPDATE articles SET japaneseHtml = :html, rubyVersion = 2 WHERE id = :id")
    suspend fun updateRubyHtml(id: Long, html: String)

    @Query("SELECT * FROM drafts WHERE articleId = :articleId")
    fun observeDraft(articleId: Long): Flow<DraftEntity?>

    @Query("SELECT * FROM article_states") fun observeArticleStates(): Flow<List<ArticleStateEntity>>
    @Query("SELECT * FROM article_states WHERE articleId = :articleId")
    fun observeArticleState(articleId: Long): Flow<ArticleStateEntity?>
    @Query("SELECT * FROM vocabulary ORDER BY savedAt DESC")
    fun observeVocabulary(): Flow<List<VocabularyEntity>>

    @Upsert suspend fun upsertArticles(articles: List<ArticleEntity>)
    @Transaction suspend fun upsertArticleBySource(article: ArticleEntity) {
        val existing = findBySourceUrl(article.sourceUrl)
        upsertArticles(listOf(if (existing == null) article else article.copy(id = existing.id)))
    }
    @Upsert suspend fun upsertDraft(draft: DraftEntity)
    @Upsert suspend fun upsertArticleState(state: ArticleStateEntity)
    @Upsert suspend fun upsertVocabulary(entry: VocabularyEntity)
    @Query("DELETE FROM vocabulary WHERE word = :word") suspend fun deleteVocabulary(word: String)
}

@Database(
    entities = [ArticleEntity::class, DraftEntity::class, ArticleStateEntity::class, VocabularyEntity::class],
    version = 4, exportSchema = false
)
abstract class CopyworkDatabase : RoomDatabase() {
    abstract fun dao(): CopyworkDao
}
