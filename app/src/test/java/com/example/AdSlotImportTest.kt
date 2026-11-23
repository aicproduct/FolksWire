package com.example

import com.example.data.model.NewsArticle
import com.example.data.model.ProviderGroup
import com.example.data.model.SponsoredAd
import com.example.data.remote.AdFetcher
import com.example.ui.viewmodel.AdImportStatus
import com.example.ui.viewmodel.FeedListItem
import com.example.ui.viewmodel.NewsViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdSlotImportTest {

    private val fetcher = AdFetcher()

    private fun sampleArticle(index: Int): NewsArticle = NewsArticle(
        id = "art-$index",
        sourceId = "wired_top",
        sourceName = "WIRED",
        providerGroup = ProviderGroup.WIRED,
        category = "Tech",
        title = "Headline $index",
        summary = "Summary $index",
        contentHtml = "<p>Summary $index</p>",
        author = "Reporter $index",
        link = "https://www.wired.com/story/$index/",
        imageUrl = null,
        hashtagsCsv = "#Tech",
        publishedAt = 1_000_000L - index,
        fetchedAt = 1_000_000L
    )

    private fun sampleAd(index: Int): SponsoredAd = SponsoredAd(
        id = "ad-$index",
        title = "Sponsor Offer $index",
        description = "Sponsored message $index",
        sponsorName = "HashtagFolks Partner $index",
        imageUrl = "https://hashtagfolks.com/media/ad_$index.jpg",
        targetUrl = "https://hashtagfolks.com/go/$index",
        ctaLabel = "Explore",
        slotPositionHint = null,
        sourceUrl = "https://hashtagfolks.com/ads.json",
        fetchedAt = 1_000_000L
    )

    @Test
    fun `parseAdJsonObject maps valid ads and filters invalid urls or blank titles`() {
        val json = """
            {
              "ads": [
                {
                  "id": "hf-01",
                  "title": "Build on HashtagFolks",
                  "description": "Reach indie builders.",
                  "sponsor": "HashtagFolks Studio",
                  "image_url": "/assets/banner.jpg",
                  "cta_text": "Get Started",
                  "target_url": "/campaigns/studio",
                  "slot_position": 2
                },
                {
                  "id": "hf-bad-scheme",
                  "title": "Unsafe Script",
                  "target_url": "javascript:alert(1)"
                },
                {
                  "id": "hf-blank-title",
                  "title": "   ",
                  "target_url": "https://hashtagfolks.com/valid"
                }
              ]
            }
        """.trimIndent()

        val result = fetcher.parseAdJsonObject(json, "https://hashtagfolks.com/ads.json", fetchedAt = 42L)
        assertEquals(1, result.size)
        val ad = result.first()
        assertEquals("hf-01", ad.id)
        assertEquals("Build on HashtagFolks", ad.title)
        assertEquals("Reach indie builders.", ad.description)
        assertEquals("HashtagFolks Studio", ad.sponsorName)
        assertEquals("https://hashtagfolks.com/assets/banner.jpg", ad.imageUrl)
        assertEquals("https://hashtagfolks.com/campaigns/studio", ad.targetUrl)
        assertEquals("Get Started", ad.ctaLabel)
        assertEquals(2, ad.slotPositionHint)
        assertEquals(42L, ad.fetchedAt)
    }

    @Test
    fun `parseAdJsonArray maps top-level array with fallback fields`() {
        val json = """
            [
              {
                "headline": "Creator Digest Pro",
                "body": "Weekly curated drops.",
                "advertiser": "Folks Media",
                "imageUrl": "https://cdn.hashtagfolks.com/pro.png",
                "cta": "Subscribe",
                "url": "https://hashtagfolks.com/pro"
              }
            ]
        """.trimIndent()

        val result = fetcher.parseAdJsonArray(json, "https://hashtagfolks.com/api/ads", fetchedAt = 99L)
        assertEquals(1, result.size)
        val ad = result.first()
        assertEquals("Creator Digest Pro", ad.title)
        assertEquals("Weekly curated drops.", ad.description)
        assertEquals("Folks Media", ad.sponsorName)
        assertEquals("https://cdn.hashtagfolks.com/pro.png", ad.imageUrl)
        assertEquals("Subscribe", ad.ctaLabel)
        assertEquals("https://hashtagfolks.com/pro", ad.targetUrl)
    }

    @Test
    fun `parseHtmlAdSlots extracts ad-slot and data-ad-slot links`() {
        val html = """
            <html>
              <body>
                <a class="promo ad-slot" href="/sponsor/cloud"><strong>Cloud Credits</strong> Deal</a>
                <a data-ad-slot="footer" href="https://hashtagfolks.com/sponsor/cli">Folks CLI</a>
              </body>
            </html>
        """.trimIndent()

        val result = fetcher.parseHtmlAdSlots(html, "https://hashtagfolks.com", fetchedAt = 55L)
        assertEquals(2, result.size)
        assertEquals("Cloud Credits Deal", result[0].title)
        assertEquals("https://hashtagfolks.com/sponsor/cloud", result[0].targetUrl)
        assertEquals("Folks CLI", result[1].title)
        assertEquals("https://hashtagfolks.com/sponsor/cli", result[1].targetUrl)
    }

    @Test
    fun `buildInterleavedFeedItems inserts ad slots at regular cadence when enabled`() {
        val articles = (1..8).map { sampleArticle(it) }
        val ads = listOf(sampleAd(1), sampleAd(2))

        val items = NewsViewModel.buildInterleavedFeedItems(
            articles = articles,
            importedAds = ads,
            adsEnabled = true,
            adFeedUrl = "https://hashtagfolks.com/ads.json",
            adStatus = AdImportStatus()
        )

        assertTrue(items.first() is FeedListItem.HeroPost)
        val adSlots = items.filterIsInstance<FeedListItem.AdSlot>()
        assertEquals(2, adSlots.size)
        assertEquals("ad-1", adSlots[0].ad?.id)
        assertEquals(1, adSlots[0].slotNumber)
        assertEquals("ad-2", adSlots[1].ad?.id)
        assertEquals(2, adSlots[1].slotNumber)
    }

    @Test
    fun `buildInterleavedFeedItems omits ad slots when ads disabled`() {
        val articles = (1..6).map { sampleArticle(it) }
        val items = NewsViewModel.buildInterleavedFeedItems(
            articles = articles,
            importedAds = listOf(sampleAd(1)),
            adsEnabled = false,
            adFeedUrl = "https://hashtagfolks.com/ads.json",
            adStatus = AdImportStatus()
        )

        assertFalse(items.any { it is FeedListItem.AdSlot })
        assertEquals(6, items.size)
    }

    @Test
    fun `buildInterleavedFeedItems shows single honest empty slot when no ads imported`() {
        val articles = (1..8).map { sampleArticle(it) }
        val items = NewsViewModel.buildInterleavedFeedItems(
            articles = articles,
            importedAds = emptyList(),
            adsEnabled = true,
            adFeedUrl = "https://hashtagfolks.com/ads.json",
            adStatus = AdImportStatus(isImporting = false, lastError = "Host unreachable (hashtagfolks.com)")
        )

        val adSlots = items.filterIsInstance<FeedListItem.AdSlot>()
        assertEquals(1, adSlots.size)
        assertNull(adSlots.first().ad)
        assertEquals("Host unreachable (hashtagfolks.com)", adSlots.first().errorMessage)
    }
}
