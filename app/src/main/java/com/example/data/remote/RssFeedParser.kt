package com.example.data.remote

import com.example.data.model.FeedSource
import com.example.data.model.NewsArticle
import com.example.data.remote.dto.JsonFeedDto
import com.example.data.remote.dto.WordPressPostDto
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory

object RssFeedParser {

    private val imgSrcRegex = Regex("""<img[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
    private val htmlTagRegex = Regex("""<[^>]+>""")
    private val whitespaceRegex = Regex("""\s+""")
    private val hashtagRegex = Regex("""(?<!\w)#([A-Za-z][A-Za-z0-9_]{1,28})""")
    private val alternateFeedRegex = Regex(
        """<link[^>]+rel=["'][^"']*alternate[^"']*["'][^>]+type=["']application/(?:rss|atom)\+xml["'][^>]+href=["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )
    private val alternateFeedRegexAltOrder = Regex(
        """<link[^>]+type=["']application/(?:rss|atom)\+xml["'][^>]+href=["']([^"']+)["']""",
        RegexOption.IGNORE_CASE
    )

    private val dateFormats = listOf(
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm:ss zzz",
        "EEE, d MMM yyyy HH:mm:ss Z",
        "EEE, d MMM yyyy HH:mm:ss zzz",
        "dd MMM yyyy HH:mm:ss Z",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd"
    )

    fun discoverAlternateFeedUrl(html: String, baseUrl: String): String? {
        val rawHref = alternateFeedRegex.find(html)?.groupValues?.getOrNull(1)
            ?: alternateFeedRegexAltOrder.find(html)?.groupValues?.getOrNull(1)
            ?: return null
        return resolveUrl(baseUrl, rawHref.trim())
    }

    fun parseXmlFeed(xml: String, source: FeedSource, fetchedAt: Long = System.currentTimeMillis()): List<NewsArticle> {
        val cleanedXml = xml.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
        if (cleanedXml.isEmpty() || !cleanedXml.contains("<")) return emptyList()

        return try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                isValidating = false
                isExpandEntityReferences = false
                try {
                    setFeature("http://xml.org/sax/features/external-general-entities", false)
                    setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                    setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                } catch (_: Exception) {
                }
            }
            val doc = factory.newDocumentBuilder().parse(InputSource(StringReader(cleanedXml)))
            doc.documentElement?.normalize()

            val articles = mutableListOf<NewsArticle>()
            val itemNodes = doc.getElementsByTagName("item")
            if (itemNodes.length > 0) {
                for (i in 0 until itemNodes.length) {
                    val element = itemNodes.item(i) as? Element ?: continue
                    parseEntryElement(element, source, fetchedAt)?.let { articles.add(it) }
                }
            } else {
                val entryNodes = doc.getElementsByTagName("entry")
                for (i in 0 until entryNodes.length) {
                    val element = entryNodes.item(i) as? Element ?: continue
                    parseEntryElement(element, source, fetchedAt)?.let { articles.add(it) }
                }
            }
            articles
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseEntryElement(
        element: Element,
        source: FeedSource,
        fetchedAt: Long
    ): NewsArticle? {
        var title = ""
        var link = ""
        var guid = ""
        var description = ""
        var contentEncoded = ""
        var author = ""
        var pubDate = ""
        var imageUrl: String? = null
        val categories = mutableListOf<String>()

        val children = element.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != Node.ELEMENT_NODE) continue
            val el = child as Element
            val tag = el.tagName.lowercase(Locale.US)

            when (tag) {
                "title" -> if (title.isBlank()) title = el.textContent.orEmpty().trim()
                "link" -> {
                    val href = el.getAttribute("href").trim()
                    val rel = el.getAttribute("rel").trim().lowercase(Locale.US)
                    val text = el.textContent.orEmpty().trim()
                    if (href.isNotBlank()) {
                        if (rel.isBlank() || rel == "alternate") {
                            link = href
                        } else if (rel == "enclosure" && imageUrl == null) {
                            val type = el.getAttribute("type").lowercase(Locale.US)
                            if (type.startsWith("image")) imageUrl = href
                        }
                    } else if (text.isNotBlank() && link.isBlank()) {
                        link = text
                    }
                }
                "guid", "id" -> if (guid.isBlank()) guid = el.textContent.orEmpty().trim()
                "description", "summary" -> {
                    val text = el.textContent.orEmpty().trim()
                    if (text.length > description.length) description = text
                }
                "content:encoded", "content" -> {
                    val text = el.textContent.orEmpty().trim()
                    if (text.length > contentEncoded.length) contentEncoded = text
                }
                "dc:creator" -> {
                    val text = el.textContent.orEmpty().trim()
                    if (text.isNotBlank() && author.isBlank()) author = text
                }
                "author" -> {
                    if (author.isBlank()) {
                        val nameNode = el.getElementsByTagName("name").item(0)
                        val text = (nameNode?.textContent ?: el.textContent).orEmpty().trim()
                        if (text.isNotBlank()) author = text
                    }
                }
                "pubdate", "published", "updated", "dc:date" -> {
                    val text = el.textContent.orEmpty().trim()
                    if (text.isNotBlank() && pubDate.isBlank()) pubDate = text
                }
                "category", "media:keywords" -> {
                    val term = el.getAttribute("term").trim()
                    val rawCat = term.ifBlank { el.textContent.orEmpty().trim() }
                    rawCat.split(",").forEach { part ->
                        val cleaned = part.trim()
                        if (cleaned.isNotBlank()) categories.add(cleaned)
                    }
                }
                "media:thumbnail", "media:content", "enclosure" -> {
                    val url = el.getAttribute("url").trim()
                    val type = el.getAttribute("type").lowercase(Locale.US)
                    val medium = el.getAttribute("medium").lowercase(Locale.US)
                    if (url.isNotBlank() && imageUrl == null) {
                        if (tag == "media:thumbnail" ||
                            medium == "image" ||
                            type.startsWith("image") ||
                            url.endsWith(".jpg", true) ||
                            url.endsWith(".jpeg", true) ||
                            url.endsWith(".png", true) ||
                            url.endsWith(".webp", true)
                        ) {
                            imageUrl = url
                        }
                    }
                }
            }
        }

        val cleanTitle = stripHtml(title)
        val resolvedLink = resolveUrl(source.url, link.ifBlank { guid })
        if (cleanTitle.isBlank() || resolvedLink.isBlank()) return null

        val rawBodyHtml = contentEncoded.ifBlank { description }
        val fallbackImg = imageUrl
            ?: imgSrcRegex.find(rawBodyHtml)?.groupValues?.getOrNull(1)
            ?: imgSrcRegex.find(description)?.groupValues?.getOrNull(1)
        val cleanSummary = stripHtml(description.ifBlank { rawBodyHtml }).take(320)
        val extractedTags = buildHashtags(
            categories = categories,
            title = cleanTitle,
            summary = cleanSummary,
            fallbackCategory = source.category
        )
        val stableId = sha256(guid.ifBlank { resolvedLink } + "|" + source.id)

        return NewsArticle(
            id = stableId,
            sourceId = source.id,
            sourceName = source.name,
            providerGroup = source.providerGroup,
            category = source.category,
            title = cleanTitle,
            summary = cleanSummary,
            contentHtml = rawBodyHtml.ifBlank { description },
            author = stripHtml(author).ifBlank { source.name },
            link = resolvedLink,
            imageUrl = fallbackImg?.let { resolveUrl(source.url, it) },
            hashtagsCsv = extractedTags.joinToString(","),
            publishedAt = parseDate(pubDate) ?: fetchedAt,
            fetchedAt = fetchedAt
        )
    }

    fun mapWordPressPosts(
        posts: List<WordPressPostDto>,
        source: FeedSource,
        fetchedAt: Long = System.currentTimeMillis()
    ): List<NewsArticle> {
        return posts.mapNotNull { post ->
            val cleanTitle = stripHtml(post.title?.rendered.orEmpty())
            val link = post.link.trim()
            if (cleanTitle.isBlank() || link.isBlank()) return@mapNotNull null

            val excerptHtml = post.excerpt?.rendered.orEmpty()
            val contentHtml = post.content?.rendered.orEmpty()
            val dateStr = post.dateGmt?.takeIf { it.isNotBlank() } ?: post.date
            val featuredImg = post.embedded?.featuredMedia?.firstOrNull()?.sourceUrl
            val img = featuredImg ?: imgSrcRegex.find(contentHtml)?.groupValues?.getOrNull(1)
            val authorName = post.embedded?.authors?.firstOrNull()?.name?.takeIf { it.isNotBlank() } ?: source.name
            val termCategories = post.embedded?.terms?.flatten().orEmpty()
                .mapNotNull { it.name?.takeIf { name -> name.isNotBlank() } }
            val summary = stripHtml(excerptHtml.ifBlank { contentHtml }).take(320)
            val tags = buildHashtags(termCategories, cleanTitle, summary, source.category)

            NewsArticle(
                id = sha256("$link|${source.id}"),
                sourceId = source.id,
                sourceName = source.name,
                providerGroup = source.providerGroup,
                category = source.category,
                title = cleanTitle,
                summary = summary,
                contentHtml = contentHtml.ifBlank { excerptHtml },
                author = stripHtml(authorName),
                link = link,
                imageUrl = img?.let { resolveUrl(source.url, it) },
                hashtagsCsv = tags.joinToString(","),
                publishedAt = parseDate(dateStr) ?: fetchedAt,
                fetchedAt = fetchedAt
            )
        }
    }

    fun mapJsonFeed(
        feed: JsonFeedDto,
        source: FeedSource,
        fetchedAt: Long = System.currentTimeMillis()
    ): List<NewsArticle> {
        return feed.items.mapNotNull { item ->
            val cleanTitle = stripHtml(item.title.orEmpty())
            val link = item.url?.trim().orEmpty().ifBlank { item.id.trim() }
            if (cleanTitle.isBlank() || link.isBlank()) return@mapNotNull null

            val bodyHtml = item.contentHtml.orEmpty().ifBlank { item.contentText.orEmpty() }
            val summary = stripHtml(item.summary.orEmpty().ifBlank { bodyHtml }).take(320)
            val author = item.authors?.firstOrNull()?.name?.takeIf { it.isNotBlank() } ?: source.name
            val tags = buildHashtags(item.tags.orEmpty(), cleanTitle, summary, source.category)
            val img = item.image ?: imgSrcRegex.find(bodyHtml)?.groupValues?.getOrNull(1)

            NewsArticle(
                id = sha256("${item.id.ifBlank { link }}|${source.id}"),
                sourceId = source.id,
                sourceName = source.name,
                providerGroup = source.providerGroup,
                category = source.category,
                title = cleanTitle,
                summary = summary,
                contentHtml = bodyHtml.ifBlank { summary },
                author = author,
                link = resolveUrl(source.url, link),
                imageUrl = img?.let { resolveUrl(source.url, it) },
                hashtagsCsv = tags.joinToString(","),
                publishedAt = parseDate(item.datePublished) ?: fetchedAt,
                fetchedAt = fetchedAt
            )
        }
    }

    fun stripHtml(raw: String): String {
        if (raw.isBlank()) return ""
        val decoded = decodeHtmlEntities(raw)
        return htmlTagRegex.replace(decoded, " ")
            .let { decodeHtmlEntities(it) }
            .replace(whitespaceRegex, " ")
            .trim()
    }

    fun htmlToReadableParagraphs(rawHtml: String): List<String> {
        if (rawHtml.isBlank()) return emptyList()
        val normalized = rawHtml
            .replace(Regex("</p>|</div>|</h[1-6]>|<br\\s*/?>|</li>", RegexOption.IGNORE_CASE), "\n\n")
            .replace(Regex("<li[^>]*>", RegexOption.IGNORE_CASE), "• ")
        val withoutTags = htmlTagRegex.replace(normalized, " ")
        val decoded = decodeHtmlEntities(withoutTags)
        return decoded
            .split(Regex("\n{2,}"))
            .map { it.replace(Regex("[ \t]+"), " ").trim() }
            .filter { it.length > 2 }
    }

    private fun decodeHtmlEntities(input: String): String {
        return input
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace("&mdash;", "—")
            .replace("&ndash;", "–")
            .replace("&hellip;", "…")
            .replace("&rsquo;", "’")
            .replace("&lsquo;", "‘")
            .replace("&rdquo;", "”")
            .replace("&ldquo;", "“")
            .replace(Regex("&#(\\d+);")) { match ->
                match.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: match.value
            }
            .replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
                match.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: match.value
            }
    }

    private fun buildHashtags(
        categories: List<String>,
        title: String,
        summary: String,
        fallbackCategory: String
    ): List<String> {
        val tags = LinkedHashSet<String>()
        hashtagRegex.findAll("$title $summary").forEach { match ->
            tags.add("#" + match.groupValues[1])
        }
        categories.forEach { cat ->
            val clean = cat.replace(Regex("[^A-Za-z0-9]"), "")
            if (clean.length in 2..24) {
                tags.add("#$clean")
            }
        }
        if (tags.isEmpty() && fallbackCategory.isNotBlank()) {
            val clean = fallbackCategory.replace(Regex("[^A-Za-z0-9]"), "")
            if (clean.isNotBlank()) tags.add("#$clean")
        }
        return tags.take(5)
    }

    fun parseDate(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        for (pattern in dateFormats) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                    isLenient = true
                }
                val parsed = sdf.parse(trimmed)
                if (parsed != null) return parsed.time
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun resolveUrl(baseUrl: String, candidate: String): String {
        val trimmed = candidate.trim()
        if (trimmed.isBlank()) return ""
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        if (trimmed.startsWith("//")) return "https:$trimmed"
        return try {
            val uri = java.net.URI(baseUrl)
            uri.resolve(trimmed).toString()
        } catch (_: Exception) {
            trimmed
        }
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(32)
    }
}
