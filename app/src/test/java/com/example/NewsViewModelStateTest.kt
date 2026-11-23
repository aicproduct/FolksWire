package com.example

import com.example.data.model.FeedSource
import com.example.data.model.NewsArticle
import com.example.data.model.ProviderGroup
import com.example.ui.viewmodel.FeedLoadState
import com.example.ui.viewmodel.NewsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsViewModelStateTest {

    private val sampleArticle = NewsArticle(
        id = "art-1",
        sourceId = "wired_top",
        sourceName = "WIRED Top Stories",
        providerGroup = ProviderGroup.WIRED,
        category = "Top",
        title = "Sample Wired Headline",
        summary = "Summary text",
        contentHtml = "<p>Summary text</p>",
        author = "WIRED Staff",
        link = "https://www.wired.com/story/sample/",
        imageUrl = null,
        hashtagsCsv = "#Tech,#Wired",
        publishedAt = 1_000_000L,
        fetchedAt = 1_000_000L
    )

    private val failingSource = FeedSource(
        id = "hashtagfolks_main",
        name = "HashtagFolks",
        providerGroup = ProviderGroup.HASHTAGFOLKS,
        category = "Community",
        url = "https://hashtagfolks.com/feed",
        isEnabled = true,
        lastStatusOk = false,
        lastError = "Host unreachable (hashtagfolks.com)"
    )

    @Test
    fun `returns Loading while initial sync is in progress with no cached articles`() {
        val state = NewsViewModel.computeFeedLoadState(
            filteredArticles = emptyList(),
            providerScopedArticlesCount = 0,
            failingSources = emptyList(),
            isRefreshing = true,
            hasCompletedInitialSync = false,
            hasActiveFilter = false
        )
        assertTrue(state is FeedLoadState.Loading)
    }

    @Test
    fun `returns Error when initial sync finished with failing sources and zero articles`() {
        val state = NewsViewModel.computeFeedLoadState(
            filteredArticles = emptyList(),
            providerScopedArticlesCount = 0,
            failingSources = listOf(failingSource),
            isRefreshing = false,
            hasCompletedInitialSync = true,
            hasActiveFilter = false
        )
        assertTrue(state is FeedLoadState.Error)
        assertEquals("Host unreachable (hashtagfolks.com)", (state as FeedLoadState.Error).message)
    }

    @Test
    fun `returns Success when cached articles are available even during background refresh`() {
        val state = NewsViewModel.computeFeedLoadState(
            filteredArticles = listOf(sampleArticle),
            providerScopedArticlesCount = 1,
            failingSources = listOf(failingSource),
            isRefreshing = true,
            hasCompletedInitialSync = false,
            hasActiveFilter = false
        )
        assertTrue(state is FeedLoadState.Success)
        val success = state as FeedLoadState.Success
        assertEquals(1, success.articles.size)
        assertTrue(success.isRefreshing)
    }
}
