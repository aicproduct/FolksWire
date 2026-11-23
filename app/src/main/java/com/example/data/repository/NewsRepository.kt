package com.example.data.repository

import com.example.data.local.NewsDao
import com.example.data.local.ReaderFontScale
import com.example.data.local.ReaderPreferences
import com.example.data.local.ThemeMode
import com.example.data.local.UserPreferences
import com.example.data.model.FeedSource
import com.example.data.model.NewsArticle
import com.example.data.model.ProviderGroup
import com.example.data.model.SponsoredAd
import com.example.data.remote.AdFetchResult
import com.example.data.remote.AdFetcher
import com.example.data.remote.FeedFetchResult
import com.example.data.remote.FeedFetcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import java.util.UUID

class NewsRepository(
    private val newsDao: NewsDao,
    private val feedFetcher: FeedFetcher,
    private val preferences: ReaderPreferences,
    private val adFetcher: AdFetcher = AdFetcher()
) {
    val enabledArticles: Flow<List<NewsArticle>> = newsDao.observeEnabledArticles()
    val bookmarks: Flow<List<NewsArticle>> = newsDao.observeBookmarks()
    val sources: Flow<List<FeedSource>> = newsDao.observeSources()
    val sponsoredAds: Flow<List<SponsoredAd>> = newsDao.observeSponsoredAds()
    val cachedArticleCount: Flow<Int> = newsDao.observeTotalCachedCount()
    val userPreferences: Flow<UserPreferences> = preferences.preferencesFlow

    fun observeArticle(id: String): Flow<NewsArticle?> = newsDao.observeArticleById(id)

    suspend fun ensureDefaultSources() {
        newsDao.insertDefaultSources(defaultSources())
    }

    suspend fun refreshAllEnabledSources() = coroutineScope {
        ensureDefaultSources()
        val enabled = newsDao.getEnabledSources()
        val bookmarkedIds = newsDao.getBookmarkedIds().toHashSet()
        val readIds = newsDao.getReadIds().toHashSet()

        enabled.map { source ->
            async {
                refreshSingleInternal(source, bookmarkedIds, readIds)
            }
        }.awaitAll()

        val fourteenDaysAgo = System.currentTimeMillis() - 14L * 24L * 60L * 60L * 1000L
        newsDao.pruneOldUnbookmarkedArticles(fourteenDaysAgo)
    }

    suspend fun refreshSource(sourceId: String) {
        val source = newsDao.getSourceById(sourceId) ?: return
        val bookmarkedIds = newsDao.getBookmarkedIds().toHashSet()
        val readIds = newsDao.getReadIds().toHashSet()
        refreshSingleInternal(source, bookmarkedIds, readIds)
    }

    suspend fun fetchHashtagFolksFeed(): FeedFetchResult {
        ensureDefaultSources()
        val source = newsDao.getSourceById("hashtagfolks_main")
            ?: defaultSources().first { it.id == "hashtagfolks_main" }
        val result = feedFetcher.fetchSource(source)
        if (result is FeedFetchResult.Success) {
            val bookmarkedIds = newsDao.getBookmarkedIds().toHashSet()
            val readIds = newsDao.getReadIds().toHashSet()
            newsDao.upsertArticles(
                result.articles.map {
                    it.copy(
                        isBookmarked = bookmarkedIds.contains(it.id),
                        isRead = readIds.contains(it.id)
                    )
                }
            )
            newsDao.updateSourceSyncStatus(source.id, System.currentTimeMillis(), ok = true, error = null)
        } else if (result is FeedFetchResult.Failure) {
            newsDao.updateSourceSyncStatus(source.id, System.currentTimeMillis(), ok = false, error = result.message)
        }
        return result
    }

    suspend fun fetchWiredNewsFeed(): List<FeedFetchResult> = coroutineScope {
        ensureDefaultSources()
        val wiredSources = newsDao.getAllSources().filter {
            it.providerGroup == ProviderGroup.WIRED && it.isEnabled
        }
        val bookmarkedIds = newsDao.getBookmarkedIds().toHashSet()
        val readIds = newsDao.getReadIds().toHashSet()
        wiredSources.map { source ->
            async {
                val result = feedFetcher.fetchSource(source)
                if (result is FeedFetchResult.Success) {
                    newsDao.upsertArticles(
                        result.articles.map {
                            it.copy(
                                isBookmarked = bookmarkedIds.contains(it.id),
                                isRead = readIds.contains(it.id)
                            )
                        }
                    )
                    newsDao.updateSourceSyncStatus(source.id, System.currentTimeMillis(), ok = true, error = null)
                } else if (result is FeedFetchResult.Failure) {
                    newsDao.updateSourceSyncStatus(source.id, System.currentTimeMillis(), ok = false, error = result.message)
                }
                result
            }
        }.awaitAll()
    }

    private suspend fun refreshSingleInternal(
        source: FeedSource,
        bookmarkedIds: Set<String>,
        readIds: Set<String>
    ) {
        val now = System.currentTimeMillis()
        when (val result = feedFetcher.fetchSource(source)) {
            is FeedFetchResult.Success -> {
                val merged = result.articles.map { article ->
                    article.copy(
                        isBookmarked = bookmarkedIds.contains(article.id),
                        isRead = readIds.contains(article.id)
                    )
                }
                newsDao.upsertArticles(merged)
                newsDao.updateSourceSyncStatus(
                    id = source.id,
                    syncedAt = now,
                    ok = true,
                    error = null
                )
            }
            is FeedFetchResult.Failure -> {
                newsDao.updateSourceSyncStatus(
                    id = source.id,
                    syncedAt = now,
                    ok = false,
                    error = result.message
                )
            }
        }
    }

    suspend fun toggleBookmark(articleId: String, currentlyBookmarked: Boolean) {
        newsDao.updateBookmark(articleId, !currentlyBookmarked)
    }

    suspend fun markAsRead(articleId: String) {
        newsDao.updateRead(articleId, true)
    }

    suspend fun setSourceEnabled(sourceId: String, enabled: Boolean) {
        newsDao.setSourceEnabled(sourceId, enabled)
        if (enabled) {
            refreshSource(sourceId)
        }
    }

    suspend fun updateSourceUrl(sourceId: String, newUrl: String) {
        val cleaned = normalizeUrl(newUrl)
        if (cleaned.isBlank()) return
        newsDao.updateSourceUrl(sourceId, cleaned)
        newsDao.deleteUnbookmarkedBySource(sourceId)
        refreshSource(sourceId)
    }

    suspend fun addCustomSource(name: String, url: String, category: String) {
        val cleanedUrl = normalizeUrl(url)
        if (name.isBlank() || cleanedUrl.isBlank()) return
        val id = "custom_${UUID.randomUUID().toString().take(8)}"
        val source = FeedSource(
            id = id,
            name = name.trim(),
            providerGroup = ProviderGroup.CUSTOM,
            category = category.trim().ifBlank { "Custom" },
            url = cleanedUrl,
            isEnabled = true,
            isDefault = false
        )
        newsDao.upsertSource(source)
        refreshSource(id)
    }

    suspend fun deleteCustomSource(sourceId: String) {
        newsDao.deleteUnbookmarkedBySource(sourceId)
        newsDao.deleteCustomSource(sourceId)
    }

    suspend fun clearCachedArticles() {
        newsDao.clearUnbookmarkedArticles()
    }

    suspend fun setThemeMode(mode: ThemeMode) = preferences.setThemeMode(mode)

    suspend fun setFontScale(scale: ReaderFontScale) = preferences.setFontScale(scale)

    suspend fun setAdsEnabled(enabled: Boolean) = preferences.setAdsEnabled(enabled)

    suspend fun setAdFeedUrl(url: String): AdFetchResult {
        val normalized = normalizeUrl(url).ifBlank { UserPreferences.DEFAULT_AD_FEED_URL }
        preferences.setAdFeedUrl(normalized)
        return refreshHashtagFolksAds(normalized)
    }

    suspend fun refreshHashtagFolksAds(overrideUrl: String? = null): AdFetchResult {
        val prefs = userPreferences.first()
        if (!prefs.adsEnabled) {
            return AdFetchResult.Success(emptyList(), prefs.adFeedUrl)
        }
        val targetUrl = overrideUrl ?: prefs.adFeedUrl
        val result = adFetcher.fetchAds(targetUrl)
        if (result is AdFetchResult.Success) {
            newsDao.replaceSponsoredAds(result.ads)
        }
        return result
    }

    private fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    companion object {
        fun defaultSources(): List<FeedSource> = listOf(
            FeedSource(
                id = "hashtagfolks_main",
                name = "HashtagFolks",
                providerGroup = ProviderGroup.HASHTAGFOLKS,
                category = "Community",
                url = "https://hashtagfolks.com/feed",
                isEnabled = true,
                isDefault = true
            ),
            FeedSource(
                id = "wired_top",
                name = "WIRED Top Stories",
                providerGroup = ProviderGroup.WIRED,
                category = "Top",
                url = "https://www.wired.com/feed/rss",
                isEnabled = true,
                isDefault = true
            ),
            FeedSource(
                id = "wired_science",
                name = "WIRED Science",
                providerGroup = ProviderGroup.WIRED,
                category = "Science",
                url = "https://www.wired.com/feed/category/science/latest/rss",
                isEnabled = true,
                isDefault = true
            ),
            FeedSource(
                id = "wired_security",
                name = "WIRED Security",
                providerGroup = ProviderGroup.WIRED,
                category = "Security",
                url = "https://www.wired.com/feed/category/security/latest/rss",
                isEnabled = true,
                isDefault = true
            ),
            FeedSource(
                id = "wired_ai",
                name = "WIRED AI",
                providerGroup = ProviderGroup.WIRED,
                category = "AI",
                url = "https://www.wired.com/feed/tag/ai/latest/rss",
                isEnabled = true,
                isDefault = true
            ),
            FeedSource(
                id = "wired_culture",
                name = "WIRED Culture",
                providerGroup = ProviderGroup.WIRED,
                category = "Culture",
                url = "https://www.wired.com/feed/category/culture/latest/rss",
                isEnabled = false,
                isDefault = true
            ),
            FeedSource(
                id = "wired_business",
                name = "WIRED Business",
                providerGroup = ProviderGroup.WIRED,
                category = "Business",
                url = "https://www.wired.com/feed/category/business/latest/rss",
                isEnabled = false,
                isDefault = true
            ),
            FeedSource(
                id = "wired_gear",
                name = "WIRED Gear",
                providerGroup = ProviderGroup.WIRED,
                category = "Gear",
                url = "https://www.wired.com/feed/category/gear/latest/rss",
                isEnabled = false,
                isDefault = true
            )
        )
    }
}
