package com.example

import com.example.data.model.FeedSource
import com.example.data.model.ProviderGroup
import com.example.data.remote.RssFeedParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RssFeedParserTest {

    private val wiredSource = FeedSource(
        id = "wired_top",
        name = "WIRED Top Stories",
        providerGroup = ProviderGroup.WIRED,
        category = "Top",
        url = "https://www.wired.com/feed/rss"
    )

    private val folksSource = FeedSource(
        id = "hashtagfolks_main",
        name = "HashtagFolks",
        providerGroup = ProviderGroup.HASHTAGFOLKS,
        category = "Community",
        url = "https://hashtagfolks.com/feed"
    )

    @Test
    fun `parses wired rss item with media thumbnail and categories`() {
        val sampleRss = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/" xmlns:dc="http://purl.org/dc/elements/1.1/">
              <channel>
                <title>WIRED</title>
                <item>
                  <title>Inside the Next Wave of Open-Source Silicon</title>
                  <link>https://www.wired.com/story/open-source-silicon/</link>
                  <guid>https://www.wired.com/story/open-source-silicon/</guid>
                  <pubDate>Mon, 23 Nov 2026 08:30:00 +0000</pubDate>
                  <description><![CDATA[<p>Engineers are building #RISCV chips for edge AI devices.</p>]]></description>
                  <dc:creator>Will Knight</dc:creator>
                  <category>AI</category>
                  <category>Hardware</category>
                  <media:thumbnail url="https://media.wired.com/photos/test-silicon.jpg" />
                </item>
              </channel>
            </rss>
        """.trimIndent()

        val articles = RssFeedParser.parseXmlFeed(sampleRss, wiredSource, fetchedAt = 1_000_000L)
        assertEquals(1, articles.size)
        val article = articles.first()
        assertEquals("Inside the Next Wave of Open-Source Silicon", article.title)
        assertEquals("Will Knight", article.author)
        assertEquals("https://media.wired.com/photos/test-silicon.jpg", article.imageUrl)
        assertTrue(article.hashtags.contains("#RISCV"))
        assertTrue(article.hashtags.contains("#AI"))
        assertTrue(article.summary.contains("Engineers are building"))
    }

    @Test
    fun `parses atom entry and extracts alternate link`() {
        val sampleAtom = """
            <?xml version="1.0" encoding="utf-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom">
              <title>HashtagFolks</title>
              <entry>
                <title>Creator Economy #OpenWeb Dispatch</title>
                <link rel="alternate" href="https://hashtagfolks.com/posts/open-web-dispatch" />
                <id>tag:hashtagfolks.com,2026:1</id>
                <published>2026-11-23T07:15:00Z</published>
                <summary>How independent creators wire decentralized feeds.</summary>
              </entry>
            </feed>
        """.trimIndent()

        val articles = RssFeedParser.parseXmlFeed(sampleAtom, folksSource, fetchedAt = 1_000_000L)
        assertEquals(1, articles.size)
        val article = articles.first()
        assertEquals("https://hashtagfolks.com/posts/open-web-dispatch", article.link)
        assertTrue(article.hashtags.contains("#OpenWeb"))
    }

    @Test
    fun `discovers alternate rss link from html homepage`() {
        val html = """
            <!doctype html>
            <html>
              <head>
                <link rel="alternate" type="application/rss+xml" title="HashtagFolks RSS" href="/rss.xml" />
              </head>
              <body>Hello</body>
            </html>
        """.trimIndent()

        val discovered = RssFeedParser.discoverAlternateFeedUrl(html, "https://hashtagfolks.com")
        assertNotNull(discovered)
        assertEquals("https://hashtagfolks.com/rss.xml", discovered)
    }
}
