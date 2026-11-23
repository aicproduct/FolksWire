package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.DynamicFeed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.FeedSource
import com.example.data.model.NewsArticle
import com.example.ui.components.CompactArticleCard
import com.example.ui.components.EditSourceUrlDialog
import com.example.ui.components.HashtagChip
import com.example.ui.components.HeroArticleCard
import com.example.ui.components.SourceStatusBanner
import com.example.ui.util.shareArticle
import com.example.ui.viewmodel.FeedLoadState
import com.example.ui.viewmodel.FolksWireUiState
import com.example.ui.viewmodel.ProviderFilter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    uiState: FolksWireUiState,
    articlesState: FeedLoadState,
    onRefreshAll: () -> Unit,
    onRefreshSource: (String) -> Unit,
    onUpdateSourceUrl: (String, String) -> Unit,
    onSelectProvider: (ProviderFilter) -> Unit,
    onSelectCategory: (String?) -> Unit,
    onSelectHashtag: (String?) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleSearch: (Boolean) -> Unit,
    onClearFilters: () -> Unit,
    onArticleClick: (NewsArticle) -> Unit,
    onToggleBookmark: (NewsArticle) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var editingSource by remember { mutableStateOf<FeedSource?>(null) }

    editingSource?.let { src ->
        EditSourceUrlDialog(
            source = src,
            onDismiss = { editingSource = null },
            onConfirm = { newUrl ->
                onUpdateSourceUrl(src.id, newUrl)
                editingSource = null
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Editorial Masthead Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.masthead_title),
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = stringResource(R.string.masthead_subtitle),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { onToggleSearch(!uiState.filter.isSearchActive) },
                    modifier = Modifier.testTag("toggle_search_button")
                ) {
                    Icon(
                        imageVector = if (uiState.filter.isSearchActive) Icons.Filled.Close else Icons.Filled.Search,
                        contentDescription = stringResource(
                            if (uiState.filter.isSearchActive) R.string.action_close_search else R.string.action_search
                        ),
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                IconButton(
                    onClick = onRefreshAll,
                    modifier = Modifier.testTag("refresh_feed_button")
                ) {
                    if (uiState.isRefreshing) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.action_refresh),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }
            }
        }

        AnimatedVisibility(visible = uiState.filter.isSearchActive) {
            OutlinedTextField(
                value = uiState.filter.searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    if (uiState.filter.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("feed_search_input")
            )
        }

        // Source Group Tabs (All Wire / HashtagFolks / WIRED)
        val providerTabs = ProviderFilter.entries
        PrimaryTabRow(
            selectedTabIndex = providerTabs.indexOf(uiState.filter.provider),
            containerColor = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground
        ) {
            providerTabs.forEach { tab ->
                Tab(
                    selected = uiState.filter.provider == tab,
                    onClick = { onSelectProvider(tab) },
                    text = {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1
                        )
                    },
                    modifier = Modifier.testTag("provider_tab_${tab.name.lowercase()}")
                )
            }
        }

        if (uiState.isRefreshing) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp)
            )
        } else {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }

        AnimatedVisibility(visible = uiState.isOffline) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("offline_cache_banner")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (uiState.cachedCount > 0) {
                            "Offline mode • ${uiState.cachedCount} cached stories available locally"
                        } else {
                            "Offline mode • Connect to sync RSS feeds"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Category + Trending Hashtags Rail
        if (uiState.availableCategories.isNotEmpty() || uiState.trendingHashtags.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (uiState.availableCategories.size > 1) {
                    items(uiState.availableCategories, key = { "cat_$it" }) { category ->
                        val selected = uiState.filter.category == category
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectCategory(category) },
                            label = {
                                Text(
                                    text = category,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.testTag("category_chip_${category.lowercase()}")
                        )
                    }
                }
                items(uiState.trendingHashtags, key = { "tag_$it" }) { tag ->
                    val selected = uiState.filter.hashtag.equals(tag, ignoreCase = true)
                    HashtagChip(
                        tag = tag,
                        isSelected = selected,
                        onClick = { onSelectHashtag(tag) },
                        modifier = Modifier.testTag("hashtag_chip_$tag")
                    )
                }
            }
        }

        // Main Content with Material 3 Pull-To-Refresh driven by sealed FeedLoadState
        val pullToRefreshState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = onRefreshAll,
            state = pullToRefreshState,
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullToRefreshState,
                    isRefreshing = uiState.isRefreshing,
                    containerColor = MaterialTheme.colorScheme.surface,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            },
            modifier = Modifier
                .fillMaxSize()
                .testTag("feed_pull_to_refresh")
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                val isWide = maxWidth >= 640.dp

                when (articlesState) {
                    is FeedLoadState.Loading -> {
                        FeedLoadingState(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("feed_loading_state")
                        )
                    }

                    is FeedLoadState.Error -> {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = 720.dp)
                                .testTag("feed_error_state")
                        ) {
                            item(key = "error_banner") {
                                SourceStatusBanner(
                                    failingSources = articlesState.failingSources,
                                    onRetry = { onRefreshSource(it.id) },
                                    onEditUrl = { editingSource = it }
                                )
                            }
                            item(key = "error_empty_cta") {
                                EmptyFeedState(
                                    hasActiveFilter = false,
                                    onClearFilters = onClearFilters,
                                    onRefresh = onRefreshAll
                                )
                            }
                        }
                    }

                    is FeedLoadState.Success -> {
                        val articles = articlesState.articles
                        if (articles.isEmpty()) {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .widthIn(max = 720.dp)
                            ) {
                                if (uiState.failingEnabledSources.isNotEmpty()) {
                                    item {
                                        SourceStatusBanner(
                                            failingSources = uiState.failingEnabledSources,
                                            onRetry = { onRefreshSource(it.id) },
                                            onEditUrl = { editingSource = it }
                                        )
                                    }
                                }
                                item {
                                    EmptyFeedState(
                                        hasActiveFilter = uiState.filter.category != null ||
                                            uiState.filter.hashtag != null ||
                                            uiState.filter.searchQuery.isNotBlank(),
                                        onClearFilters = onClearFilters,
                                        onRefresh = onRefreshAll
                                    )
                                }
                            }
                        } else if (isWide) {
                            val hero = articles.firstOrNull()
                            val rest = if (articles.size > 1) articles.drop(1) else emptyList()
                            LazyVerticalGrid(
                                columns = GridCells.Fixed(2),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier
                                    .fillMaxSize()
                                    .widthIn(max = 1040.dp)
                            ) {
                                if (!uiState.isOffline && uiState.failingEnabledSources.isNotEmpty()) {
                                    item(span = { GridItemSpan(2) }) {
                                        SourceStatusBanner(
                                            failingSources = uiState.failingEnabledSources,
                                            onRetry = { onRefreshSource(it.id) },
                                            onEditUrl = { editingSource = it }
                                        )
                                    }
                                }
                                if (hero != null) {
                                    item(span = { GridItemSpan(2) }, key = "hero_${hero.id}") {
                                        HeroArticleCard(
                                            article = hero,
                                            onClick = { onArticleClick(hero) },
                                            onToggleBookmark = { onToggleBookmark(hero) },
                                            onShare = { shareArticle(context, hero) },
                                            onHashtagClick = { onSelectHashtag(it) }
                                        )
                                    }
                                }
                                items(rest, key = { it.id }) { article ->
                                    CompactArticleCard(
                                        article = article,
                                        onClick = { onArticleClick(article) },
                                        onToggleBookmark = { onToggleBookmark(article) },
                                        onShare = { shareArticle(context, article) },
                                        onHashtagClick = { onSelectHashtag(it) }
                                    )
                                }
                            }
                        } else {
                            val hero = articles.firstOrNull()
                            val rest = if (articles.size > 1) articles.drop(1) else emptyList()
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                if (!uiState.isOffline && uiState.failingEnabledSources.isNotEmpty()) {
                                    item(key = "status_banner") {
                                        SourceStatusBanner(
                                            failingSources = uiState.failingEnabledSources,
                                            onRetry = { onRefreshSource(it.id) },
                                            onEditUrl = { editingSource = it }
                                        )
                                    }
                                }
                                if (hero != null) {
                                    item(key = "hero_${hero.id}") {
                                        HeroArticleCard(
                                            article = hero,
                                            onClick = { onArticleClick(hero) },
                                            onToggleBookmark = { onToggleBookmark(hero) },
                                            onShare = { shareArticle(context, hero) },
                                            onHashtagClick = { onSelectHashtag(it) }
                                        )
                                    }
                                }
                                items(rest, key = { it.id }) { article ->
                                    CompactArticleCard(
                                        article = article,
                                        onClick = { onArticleClick(article) },
                                        onToggleBookmark = { onToggleBookmark(article) },
                                        onShare = { shareArticle(context, article) },
                                        onHashtagClick = { onSelectHashtag(it) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedLoadingState(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(32.dp)
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "WIRING LATEST STORIES",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Fetching RSS feeds from hashtagfolks.com & WIRED News…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun EmptyFeedState(
    hasActiveFilter: Boolean,
    onClearFilters: () -> Unit,
    onRefresh: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.DynamicFeed,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.empty_feed_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.empty_feed_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        if (hasActiveFilter) {
            Button(onClick = onClearFilters, modifier = Modifier.testTag("clear_filters_button")) {
                Text("Clear Filters")
            }
        } else {
            Button(onClick = onRefresh, modifier = Modifier.testTag("empty_retry_button")) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sync Wire")
            }
        }
    }
}
