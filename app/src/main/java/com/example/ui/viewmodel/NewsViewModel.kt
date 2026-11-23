package com.example.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.NetworkConnectivityObserver
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
import com.example.data.remote.FeedFetcher
import com.example.data.remote.NewsFeedApiService
import com.example.data.repository.NewsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface FeedLoadState {
    data object Loading : FeedLoadState
    data class Success(
        val articles: List<NewsArticle>,
        val isRefreshing: Boolean = false
    ) : FeedLoadState
    data class Error(
        val message: String,
        val failingSources: List<FeedSource> = emptyList()
    ) : FeedLoadState
}

enum class ProviderFilter(val label: String) {
    ALL("All Wire"),
    HASHTAGFOLKS("HashtagFolks"),
    WIRED("WIRED")
}

data class FeedFilterState(
    val provider: ProviderFilter = ProviderFilter.ALL,
    val category: String? = null,
    val hashtag: String? = null,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false
)

sealed interface FeedListItem {
    val itemKey: String

    data class HeroPost(val article: NewsArticle) : FeedListItem {
        override val itemKey: String = "hero_${article.id}"
    }

    data class PostItem(val article: NewsArticle) : FeedListItem {
        override val itemKey: String = "post_${article.id}"
    }

    data class AdSlot(
        val slotNumber: Int,
        val ad: SponsoredAd?,
        val adFeedUrl: String,
        val isImporting: Boolean,
        val errorMessage: String?
    ) : FeedListItem {
        override val itemKey: String = "ad_slot_${slotNumber}_${ad?.id ?: "empty"}"
    }
}

data class AdImportStatus(
    val isImporting: Boolean = false,
    val lastError: String? = null,
    val lastResolvedUrl: String? = null
)

data class FolksWireUiState(
    val loadState: FeedLoadState = FeedLoadState.Loading,
    val filteredArticles: List<NewsArticle> = emptyList(),
    val feedItems: List<FeedListItem> = emptyList(),
    val importedAds: List<SponsoredAd> = emptyList(),
    val adImportStatus: AdImportStatus = AdImportStatus(),
    val bookmarks: List<NewsArticle> = emptyList(),
    val sources: List<FeedSource> = emptyList(),
    val failingEnabledSources: List<FeedSource> = emptyList(),
    val availableCategories: List<String> = emptyList(),
    val trendingHashtags: List<String> = emptyList(),
    val filter: FeedFilterState = FeedFilterState(),
    val isRefreshing: Boolean = false,
    val hasCompletedInitialSync: Boolean = false,
    val isOffline: Boolean = false,
    val cachedCount: Int = 0,
    val preferences: UserPreferences = UserPreferences()
)

class NewsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: NewsRepository

    private val filterState = MutableStateFlow(FeedFilterState())
    private val refreshingState = MutableStateFlow(false)
    private val initialSyncDoneState = MutableStateFlow(false)
    private val isOnlineState = MutableStateFlow(true)
    private val adImportStatusState = MutableStateFlow(AdImportStatus())

    init {
        val db = AppDatabase.getInstance(application)
        val prefs = ReaderPreferences(application)
        val apiService = NewsFeedApiService.create()
        val connectivityObserver = NetworkConnectivityObserver(application)
        repository = NewsRepository(
            newsDao = db.newsDao(),
            feedFetcher = FeedFetcher(apiService),
            preferences = prefs,
            adFetcher = AdFetcher(apiService)
        )
        viewModelScope.launch {
            repository.ensureDefaultSources()
            refreshAll()
        }
        viewModelScope.launch {
            var wasOffline = false
            connectivityObserver.observeIsOnline().collect { online ->
                isOnlineState.value = online
                if (online && wasOffline) {
                    refreshAll()
                }
                wasOffline = !online
            }
        }
    }

    private data class SyncMeta(
        val isRefreshing: Boolean,
        val initialSyncDone: Boolean,
        val isOffline: Boolean,
        val cachedCount: Int,
        val ads: List<SponsoredAd>,
        val adImportStatus: AdImportStatus
    )

    private val adStateFlow = combine(
        repository.sponsoredAds,
        adImportStatusState
    ) { ads, status -> ads to status }

    private val syncMetaFlow = combine(
        refreshingState,
        initialSyncDoneState,
        isOnlineState,
        repository.cachedArticleCount,
        adStateFlow
    ) { refreshing, done, online, cachedCount, adPair ->
        SyncMeta(
            isRefreshing = refreshing,
            initialSyncDone = done,
            isOffline = !online,
            cachedCount = cachedCount,
            ads = adPair.first,
            adImportStatus = adPair.second
        )
    }

    val uiState: StateFlow<FolksWireUiState> = combine(
        repository.enabledArticles,
        repository.bookmarks,
        repository.sources,
        filterState,
        syncMetaFlow,
        repository.userPreferences
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val articles = values[0] as List<NewsArticle>
        @Suppress("UNCHECKED_CAST")
        val bookmarks = values[1] as List<NewsArticle>
        @Suppress("UNCHECKED_CAST")
        val sources = values[2] as List<FeedSource>
        val filter = values[3] as FeedFilterState
        val syncMeta = values[4] as SyncMeta
        val prefs = values[5] as UserPreferences

        val providerScoped = when (filter.provider) {
            ProviderFilter.ALL -> articles
            ProviderFilter.HASHTAGFOLKS -> articles.filter { it.providerGroup == ProviderGroup.HASHTAGFOLKS }
            ProviderFilter.WIRED -> articles.filter { it.providerGroup == ProviderGroup.WIRED }
        }

        val categories = providerScoped.map { it.category }.distinct().sorted()
        val hashtagCounts = LinkedHashMap<String, Int>()
        providerScoped.forEach { article ->
            article.hashtags.forEach { tag ->
                hashtagCounts[tag] = (hashtagCounts[tag] ?: 0) + 1
            }
        }
        val trendingTags = hashtagCounts.entries
            .sortedByDescending { it.value }
            .take(12)
            .map { it.key }

        val query = filter.searchQuery.trim().lowercase()
        val filtered = providerScoped.filter { article ->
            val catMatch = filter.category == null || article.category.equals(filter.category, ignoreCase = true)
            val tagMatch = filter.hashtag == null || article.hashtags.any { it.equals(filter.hashtag, ignoreCase = true) }
            val searchMatch = query.isEmpty() ||
                article.title.lowercase().contains(query) ||
                article.summary.lowercase().contains(query) ||
                article.author.lowercase().contains(query) ||
                article.sourceName.lowercase().contains(query) ||
                article.hashtags.any { it.lowercase().contains(query) }
            catMatch && tagMatch && searchMatch
        }

        val failingSources = sources.filter { src ->
            val matchesProvider = when (filter.provider) {
                ProviderFilter.ALL -> true
                ProviderFilter.HASHTAGFOLKS -> src.providerGroup == ProviderGroup.HASHTAGFOLKS
                ProviderFilter.WIRED -> src.providerGroup == ProviderGroup.WIRED
            }
            src.isEnabled && !src.lastStatusOk && !src.lastError.isNullOrBlank() && matchesProvider
        }

        val isRefreshing = syncMeta.isRefreshing
        val hasCompletedInitialSync = syncMeta.initialSyncDone
        val loadState = computeFeedLoadState(
            filteredArticles = filtered,
            providerScopedArticlesCount = providerScoped.size,
            failingSources = failingSources,
            isRefreshing = isRefreshing,
            hasCompletedInitialSync = hasCompletedInitialSync,
            hasActiveFilter = filter.category != null || filter.hashtag != null || query.isNotEmpty()
        )

        val feedItems = buildInterleavedFeedItems(
            articles = filtered,
            importedAds = syncMeta.ads,
            adsEnabled = prefs.adsEnabled,
            adFeedUrl = prefs.adFeedUrl,
            adStatus = syncMeta.adImportStatus
        )

        FolksWireUiState(
            loadState = loadState,
            filteredArticles = filtered,
            feedItems = feedItems,
            importedAds = syncMeta.ads,
            adImportStatus = syncMeta.adImportStatus,
            bookmarks = bookmarks,
            sources = sources,
            failingEnabledSources = failingSources,
            availableCategories = categories,
            trendingHashtags = trendingTags,
            filter = filter,
            isRefreshing = isRefreshing,
            hasCompletedInitialSync = hasCompletedInitialSync,
            isOffline = syncMeta.isOffline,
            cachedCount = syncMeta.cachedCount,
            preferences = prefs
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = FolksWireUiState()
    )

    val articlesState: StateFlow<FeedLoadState> = uiState
        .map { it.loadState }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = FeedLoadState.Loading
        )

    fun observeArticle(articleId: String): Flow<NewsArticle?> = repository.observeArticle(articleId)

    fun refreshAll() {
        if (refreshingState.value) return
        viewModelScope.launch {
            refreshingState.value = true
            try {
                repository.refreshAllEnabledSources()
                refreshHashtagFolksAdsInternal()
            } finally {
                refreshingState.value = false
                initialSyncDoneState.value = true
            }
        }
    }

    fun refreshAds() {
        viewModelScope.launch {
            refreshHashtagFolksAdsInternal()
        }
    }

    fun setAdsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAdsEnabled(enabled)
            if (enabled) {
                refreshHashtagFolksAdsInternal()
            }
        }
    }

    fun updateAdFeedUrl(url: String) {
        viewModelScope.launch {
            adImportStatusState.value = adImportStatusState.value.copy(isImporting = true, lastError = null)
            when (val res = repository.setAdFeedUrl(url)) {
                is AdFetchResult.Success -> {
                    adImportStatusState.value = AdImportStatus(
                        isImporting = false,
                        lastError = null,
                        lastResolvedUrl = res.resolvedUrl
                    )
                }
                is AdFetchResult.Failure -> {
                    adImportStatusState.value = AdImportStatus(
                        isImporting = false,
                        lastError = res.message
                    )
                }
            }
        }
    }

    private suspend fun refreshHashtagFolksAdsInternal() {
        adImportStatusState.value = adImportStatusState.value.copy(isImporting = true, lastError = null)
        when (val res = repository.refreshHashtagFolksAds()) {
            is AdFetchResult.Success -> {
                adImportStatusState.value = AdImportStatus(
                    isImporting = false,
                    lastError = null,
                    lastResolvedUrl = res.resolvedUrl
                )
            }
            is AdFetchResult.Failure -> {
                adImportStatusState.value = AdImportStatus(
                    isImporting = false,
                    lastError = res.message
                )
            }
        }
    }

    fun refreshSource(sourceId: String) {
        viewModelScope.launch {
            refreshingState.value = true
            try {
                repository.refreshSource(sourceId)
            } finally {
                refreshingState.value = false
            }
        }
    }

    fun setProviderFilter(provider: ProviderFilter) {
        filterState.value = filterState.value.copy(
            provider = provider,
            category = null,
            hashtag = null
        )
    }

    fun setCategoryFilter(category: String?) {
        val current = filterState.value.category
        filterState.value = filterState.value.copy(
            category = if (current == category) null else category
        )
    }

    fun setHashtagFilter(hashtag: String?) {
        val current = filterState.value.hashtag
        filterState.value = filterState.value.copy(
            hashtag = if (current == hashtag) null else hashtag
        )
    }

    fun applyHashtagFromReader(hashtag: String) {
        filterState.value = filterState.value.copy(
            provider = ProviderFilter.ALL,
            category = null,
            hashtag = hashtag
        )
    }

    fun setSearchQuery(query: String) {
        filterState.value = filterState.value.copy(searchQuery = query)
    }

    fun setSearchActive(active: Boolean) {
        filterState.value = filterState.value.copy(
            isSearchActive = active,
            searchQuery = if (!active) "" else filterState.value.searchQuery
        )
    }

    fun clearAllFilters() {
        filterState.value = FeedFilterState()
    }

    fun toggleBookmark(article: NewsArticle) {
        viewModelScope.launch {
            repository.toggleBookmark(article.id, article.isBookmarked)
        }
    }

    fun markAsRead(articleId: String) {
        viewModelScope.launch {
            repository.markAsRead(articleId)
        }
    }

    fun setSourceEnabled(sourceId: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.setSourceEnabled(sourceId, enabled)
        }
    }

    fun updateSourceUrl(sourceId: String, newUrl: String) {
        viewModelScope.launch {
            refreshingState.value = true
            try {
                repository.updateSourceUrl(sourceId, newUrl)
            } finally {
                refreshingState.value = false
            }
        }
    }

    fun addCustomSource(name: String, url: String, category: String) {
        viewModelScope.launch {
            refreshingState.value = true
            try {
                repository.addCustomSource(name, url, category)
            } finally {
                refreshingState.value = false
            }
        }
    }

    fun deleteCustomSource(sourceId: String) {
        viewModelScope.launch {
            repository.deleteCustomSource(sourceId)
        }
    }

    fun clearCachedArticles() {
        viewModelScope.launch {
            repository.clearCachedArticles()
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            repository.setThemeMode(mode)
        }
    }

    fun setFontScale(scale: ReaderFontScale) {
        viewModelScope.launch {
            repository.setFontScale(scale)
        }
    }

    companion object {
        fun computeFeedLoadState(
            filteredArticles: List<NewsArticle>,
            providerScopedArticlesCount: Int,
            failingSources: List<FeedSource>,
            isRefreshing: Boolean,
            hasCompletedInitialSync: Boolean,
            hasActiveFilter: Boolean
        ): FeedLoadState {
            if (filteredArticles.isNotEmpty()) {
                return FeedLoadState.Success(
                    articles = filteredArticles,
                    isRefreshing = isRefreshing
                )
            }
            if (isRefreshing && !hasCompletedInitialSync) {
                return FeedLoadState.Loading
            }
            if (!hasActiveFilter && providerScopedArticlesCount == 0 && failingSources.isNotEmpty() && !isRefreshing) {
                val primaryError = failingSources.first().lastError ?: "Unable to fetch news feeds."
                return FeedLoadState.Error(
                    message = primaryError,
                    failingSources = failingSources
                )
            }
            if (!hasCompletedInitialSync && providerScopedArticlesCount == 0) {
                return FeedLoadState.Loading
            }
            return FeedLoadState.Success(
                articles = filteredArticles,
                isRefreshing = isRefreshing
            )
        }

        fun buildInterleavedFeedItems(
            articles: List<NewsArticle>,
            importedAds: List<SponsoredAd>,
            adsEnabled: Boolean,
            adFeedUrl: String,
            adStatus: AdImportStatus
        ): List<FeedListItem> {
            if (articles.isEmpty()) return emptyList()
            val items = mutableListOf<FeedListItem>()
            items.add(FeedListItem.HeroPost(articles.first()))

            val rest = articles.drop(1)
            if (!adsEnabled) {
                rest.forEach { items.add(FeedListItem.PostItem(it)) }
                return items
            }

            if (rest.isEmpty()) {
                items.add(
                    FeedListItem.AdSlot(
                        slotNumber = 1,
                        ad = importedAds.firstOrNull(),
                        adFeedUrl = adFeedUrl,
                        isImporting = adStatus.isImporting,
                        errorMessage = adStatus.lastError
                    )
                )
                return items
            }

            var slotCounter = 0
            rest.forEachIndexed { index, article ->
                items.add(FeedListItem.PostItem(article))
                val shouldInsertSlot = (index == 1) || (index > 1 && (index - 1) % 4 == 0)
                if (shouldInsertSlot) {
                    if (importedAds.isNotEmpty()) {
                        val ad = importedAds[slotCounter % importedAds.size]
                        slotCounter++
                        items.add(
                            FeedListItem.AdSlot(
                                slotNumber = slotCounter,
                                ad = ad,
                                adFeedUrl = adFeedUrl,
                                isImporting = adStatus.isImporting,
                                errorMessage = adStatus.lastError
                            )
                        )
                    } else if (slotCounter == 0) {
                        slotCounter++
                        items.add(
                            FeedListItem.AdSlot(
                                slotNumber = slotCounter,
                                ad = null,
                                adFeedUrl = adFeedUrl,
                                isImporting = adStatus.isImporting,
                                errorMessage = adStatus.lastError
                            )
                        )
                    }
                }
            }

            if (slotCounter == 0) {
                items.add(
                    FeedListItem.AdSlot(
                        slotNumber = 1,
                        ad = importedAds.firstOrNull(),
                        adFeedUrl = adFeedUrl,
                        isImporting = adStatus.isImporting,
                        errorMessage = adStatus.lastError
                    )
                )
            }
            return items
        }
    }
}
