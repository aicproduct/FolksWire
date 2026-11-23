package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.data.model.FeedSource
import com.example.data.model.NewsArticle
import com.example.data.model.SponsoredAd
import kotlinx.coroutines.flow.Flow

@Dao
interface NewsDao {
    @Query(
        """
        SELECT a.* FROM articles a
        INNER JOIN feed_sources s ON a.sourceId = s.id
        WHERE s.isEnabled = 1
        ORDER BY a.publishedAt DESC, a.fetchedAt DESC
        """
    )
    fun observeEnabledArticles(): Flow<List<NewsArticle>>

    @Query("SELECT * FROM articles WHERE isBookmarked = 1 ORDER BY publishedAt DESC, fetchedAt DESC")
    fun observeBookmarks(): Flow<List<NewsArticle>>

    @Query("SELECT * FROM articles WHERE id = :id LIMIT 1")
    fun observeArticleById(id: String): Flow<NewsArticle?>

    @Query("SELECT * FROM feed_sources ORDER BY isDefault DESC, providerGroup ASC, name ASC")
    fun observeSources(): Flow<List<FeedSource>>

    @Query("SELECT * FROM feed_sources WHERE isEnabled = 1")
    suspend fun getEnabledSources(): List<FeedSource>

    @Query("SELECT * FROM feed_sources")
    suspend fun getAllSources(): List<FeedSource>

    @Query("SELECT * FROM feed_sources WHERE id = :id LIMIT 1")
    suspend fun getSourceById(id: String): FeedSource?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDefaultSources(sources: List<FeedSource>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSource(source: FeedSource)

    @Query("DELETE FROM feed_sources WHERE id = :id AND isDefault = 0")
    suspend fun deleteCustomSource(id: String)

    @Query("DELETE FROM articles WHERE sourceId = :sourceId AND isBookmarked = 0")
    suspend fun deleteUnbookmarkedBySource(sourceId: String)

    @Query("SELECT id FROM articles WHERE isBookmarked = 1")
    suspend fun getBookmarkedIds(): List<String>

    @Query("SELECT id FROM articles WHERE isRead = 1")
    suspend fun getReadIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArticles(articles: List<NewsArticle>)

    @Query("UPDATE articles SET isBookmarked = :bookmarked WHERE id = :id")
    suspend fun updateBookmark(id: String, bookmarked: Boolean)

    @Query("UPDATE articles SET isRead = :read WHERE id = :id")
    suspend fun updateRead(id: String, read: Boolean)

    @Query("SELECT COUNT(*) FROM articles")
    fun observeTotalCachedCount(): Flow<Int>

    @Query("DELETE FROM articles WHERE isBookmarked = 0")
    suspend fun clearUnbookmarkedArticles()

    @Query("DELETE FROM articles WHERE isBookmarked = 0 AND fetchedAt < :cutoffMillis")
    suspend fun pruneOldUnbookmarkedArticles(cutoffMillis: Long)

    @Query(
        """
        UPDATE feed_sources
        SET lastSyncedAt = :syncedAt, lastStatusOk = :ok, lastError = :error
        WHERE id = :id
        """
    )
    suspend fun updateSourceSyncStatus(id: String, syncedAt: Long, ok: Boolean, error: String?)

    @Query("UPDATE feed_sources SET isEnabled = :enabled WHERE id = :id")
    suspend fun setSourceEnabled(id: String, enabled: Boolean)

    @Query("UPDATE feed_sources SET url = :url, lastStatusOk = 1, lastError = NULL WHERE id = :id")
    suspend fun updateSourceUrl(id: String, url: String)

    @Query("SELECT * FROM sponsored_ads ORDER BY slotPositionHint ASC, fetchedAt DESC")
    fun observeSponsoredAds(): Flow<List<SponsoredAd>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSponsoredAds(ads: List<SponsoredAd>)

    @Query("DELETE FROM sponsored_ads")
    suspend fun clearSponsoredAds()

    @Transaction
    suspend fun replaceSponsoredAds(ads: List<SponsoredAd>) {
        clearSponsoredAds()
        if (ads.isNotEmpty()) {
            upsertSponsoredAds(ads)
        }
    }
}
