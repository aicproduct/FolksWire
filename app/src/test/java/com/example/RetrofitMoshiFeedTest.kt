package com.example

import com.example.data.model.FeedSource
import com.example.data.model.ProviderGroup
import com.example.data.remote.NewsFeedApiService
import com.example.data.remote.RssFeedParser
import com.example.data.remote.dto.JsonFeedDto
import com.example.data.remote.dto.WordPressPostDto
import com.squareup.moshi.Types
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RetrofitMoshiFeedTest {

    private val folksSource = FeedSource(
        id = "hashtagfolks_main",
        name = "HashtagFolks",
        providerGroup = ProviderGroup.HASHTAGFOLKS,
        category = "Community",
        url = "https://hashtagfolks.com/feed"
    )

    @Test
    fun `creates retrofit NewsFeedApiService with Moshi converter`() {
        val service = NewsFeedApiService.create()
        assertNotNull(service)
    }

    @Test
    fun `moshi deserializes WordPress posts and maps to NewsArticle`() {
        val json = """
            [
              {
                "id": 101,
                "link": "https://hashtagfolks.com/2026/11/open-wire",
                "date_gmt": "2026-11-23T09:00:00",
                "title": { "rendered": "Building #OpenWire with Kotlin" },
                "excerpt": { "rendered": "<p>A quick look at decentralized feeds.</p>" },
                "content": { "rendered": "<p>Full guide on RSS and JSON Feeds.</p>" },
                "_embedded": {
                  "author": [{ "name": "Maya Lin" }],
                  "wp:featuredmedia": [{ "source_url": "https://hashtagfolks.com/media/cover.jpg" }],
                  "wp:term": [[{ "name": "Android" }, { "name": "RSS" }]]
                }
              }
            ]
        """.trimIndent()

        val moshi = NewsFeedApiService.createMoshi()
        val listType = Types.newParameterizedType(List::class.java, WordPressPostDto::class.java)
        val adapter = moshi.adapter<List<WordPressPostDto>>(listType)
        val dtos = adapter.fromJson(json).orEmpty()

        val articles = RssFeedParser.mapWordPressPosts(dtos, folksSource, fetchedAt = 1_000_000L)
        assertEquals(1, articles.size)
        val article = articles.first()
        assertEquals("Building #OpenWire with Kotlin", article.title)
        assertEquals("Maya Lin", article.author)
        assertEquals("https://hashtagfolks.com/media/cover.jpg", article.imageUrl)
        assertTrue(article.hashtags.contains("#OpenWire"))
        assertTrue(article.hashtags.contains("#Android"))
    }

    @Test
    fun `moshi deserializes JsonFeedDto and maps to NewsArticle`() {
        val json = """
            {
              "version": "https://jsonfeed.org/version/1.1",
              "title": "HashtagFolks",
              "items": [
                {
                  "id": "hf-42",
                  "url": "https://hashtagfolks.com/story/42",
                  "title": "Weekly #TechWire Highlights",
                  "summary": "Top community stories from across the web.",
                  "date_published": "2026-11-23T08:00:00Z",
                  "tags": ["News", "Community"],
                  "authors": [{ "name": "Samir Patel" }]
                }
              ]
            }
        """.trimIndent()

        val moshi = NewsFeedApiService.createMoshi()
        val dto = moshi.adapter(JsonFeedDto::class.java).fromJson(json)
        assertNotNull(dto)

        val articles = RssFeedParser.mapJsonFeed(dto!!, folksSource, fetchedAt = 1_000_000L)
        assertEquals(1, articles.size)
        assertEquals("Samir Patel", articles.first().author)
        assertTrue(articles.first().hashtags.contains("#TechWire"))
        assertTrue(articles.first().hashtags.contains("#News"))
    }
}
