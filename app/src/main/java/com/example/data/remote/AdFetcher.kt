package com.example.data.remote

import com.example.data.model.FeedSource
import com.example.data.model.ProviderGroup
import com.example.data.model.SponsoredAd
import com.example.data.remote.dto.AdFeedResponseDto
import com.example.data.remote.dto.AdSlotDto
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.security.MessageDigest

sealed class AdFetchResult {
    data class Success(val ads: List<SponsoredAd>, val resolvedUrl: String) : AdFetchResult()
    data class Failure(val message: String) : AdFetchResult()
}

class AdFetcher(
    private val apiService: NewsFeedApiService = NewsFeedApiService.create(),
    private val moshi: Moshi = NewsFeedApiService.createMoshi()
) {
    suspend fun fetchAds(configuredUrl: String): AdFetchResult = withContext(Dispatchers.IO) {
        val candidates = buildAdCandidateUrls(configuredUrl)
        var lastError = "Could not import ads from hashtagfolks.com"

        for (candidateUrl in candidates) {
            try {
                val response = apiService.fetchRawFeed(candidateUrl)
                if (!response.isSuccessful) {
                    lastError = "HTTP ${response.code()} from $candidateUrl"
                    continue
                }
                val body = response.body()?.string().orEmpty().trim()
                if (body.isBlank()) {
                    lastError = "Empty ad response from $candidateUrl"
                    continue
                }

                // 1. JSON Object ({ "ads": [...] } or { "items": [...] })
                if (body.startsWith("{")) {
                    val parsed = parseAdJsonObject(body, candidateUrl)
                    if (parsed.isNotEmpty()) {
                        return@withContext AdFetchResult.Success(parsed, candidateUrl)
                    }
                    lastError = "No active ad slots found in $candidateUrl"
                    continue
                }

                // 2. JSON Array ([{ ... }])
                if (body.startsWith("[")) {
                    val parsed = parseAdJsonArray(body, candidateUrl)
                    if (parsed.isNotEmpty()) {
                        return@withContext AdFetchResult.Success(parsed, candidateUrl)
                    }
                    lastError = "Empty ad list at $candidateUrl"
                    continue
                }

                // 3. RSS / Atom Sponsored Feed
                if (body.startsWith("<") &&
                    (body.contains("<rss", ignoreCase = true) ||
                        body.contains("<feed", ignoreCase = true) ||
                        body.contains("<channel", ignoreCase = true))
                ) {
                    val parsed = parseRssAsAds(body, candidateUrl)
                    if (parsed.isNotEmpty()) {
                        return@withContext AdFetchResult.Success(parsed, candidateUrl)
                    }
                    lastError = "No ad items in RSS feed $candidateUrl"
                    continue
                }

                // 4. HTML Ad Manifest Discovery / Embedded data-ad attributes
                if (body.contains("<html", ignoreCase = true)) {
                    val htmlAds = parseHtmlAdSlots(body, candidateUrl)
                    if (htmlAds.isNotEmpty()) {
                        return@withContext AdFetchResult.Success(htmlAds, candidateUrl)
                    }
                    lastError = "No ad slots discovered on $candidateUrl"
                }
            } catch (e: IOException) {
                val msg = e.message ?: "Network error"
                val hostMissing = msg.contains("Unable to resolve host", ignoreCase = true) ||
                    msg.contains("NXDOMAIN", ignoreCase = true)
                lastError = if (hostMissing) {
                    "Host unreachable (${candidateUrl.substringAfter("://").substringBefore("/")})"
                } else {
                    "Connection error: $msg"
                }
                if (hostMissing) {
                    return@withContext AdFetchResult.Failure(lastError)
                }
            } catch (e: Exception) {
                lastError = "Ad parse error: ${e.message ?: "Unknown"}"
            }
        }
        AdFetchResult.Failure(lastError)
    }

    fun parseAdJsonObject(json: String, sourceUrl: String, fetchedAt: Long = System.currentTimeMillis()): List<SponsoredAd> {
        return try {
            val dto = moshi.adapter(AdFeedResponseDto::class.java).fromJson(json) ?: return emptyList()
            val list = dto.ads ?: dto.items ?: dto.data ?: emptyList()
            mapDtos(list, sourceUrl, fetchedAt)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun parseAdJsonArray(json: String, sourceUrl: String, fetchedAt: Long = System.currentTimeMillis()): List<SponsoredAd> {
        return try {
            val listType = Types.newParameterizedType(List::class.java, AdSlotDto::class.java)
            val list = moshi.adapter<List<AdSlotDto>>(listType).fromJson(json).orEmpty()
            mapDtos(list, sourceUrl, fetchedAt)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun mapDtos(dtos: List<AdSlotDto>, sourceUrl: String, fetchedAt: Long): List<SponsoredAd> {
        return dtos.mapIndexedNotNull { index, dto ->
            val title = (dto.title ?: dto.headline).orEmpty().trim()
            val rawTarget = (dto.targetUrlSnake ?: dto.targetUrlCamel ?: dto.url ?: dto.link).orEmpty().trim()
            val targetUrl = resolveHttpUrl(sourceUrl, rawTarget)
            if (title.isBlank() || targetUrl.isBlank()) return@mapIndexedNotNull null

            val description = (dto.description ?: dto.body).orEmpty().trim()
            val sponsor = (dto.sponsor ?: dto.advertiser).orEmpty().trim().ifBlank { "HashtagFolks Sponsor" }
            val imageUrl = (dto.imageUrlSnake ?: dto.imageUrlCamel ?: dto.image)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let { resolveHttpUrl(sourceUrl, it) }
            val cta = (dto.ctaTextSnake ?: dto.cta).orEmpty().trim().ifBlank { "Learn More" }
            val id = dto.id?.takeIf { it.isNotBlank() } ?: sha256("$targetUrl|$title|$index")

            SponsoredAd(
                id = id,
                title = title,
                description = description,
                sponsorName = sponsor,
                imageUrl = imageUrl,
                targetUrl = targetUrl,
                ctaLabel = cta,
                slotPositionHint = dto.slotPosition,
                sourceUrl = sourceUrl,
                fetchedAt = fetchedAt
            )
        }
    }

    private fun parseRssAsAds(xml: String, sourceUrl: String, fetchedAt: Long = System.currentTimeMillis()): List<SponsoredAd> {
        val dummySource = FeedSource(
            id = "hashtagfolks_ads",
            name = "HashtagFolks Sponsored",
            providerGroup = ProviderGroup.HASHTAGFOLKS,
            category = "Sponsored",
            url = sourceUrl
        )
        return RssFeedParser.parseXmlFeed(xml, dummySource, fetchedAt).map { article ->
            SponsoredAd(
                id = "rss_ad_${article.id}",
                title = article.title,
                description = article.summary,
                sponsorName = article.author.ifBlank { "HashtagFolks Sponsor" },
                imageUrl = article.imageUrl,
                targetUrl = article.link,
                ctaLabel = "Visit Sponsor",
                slotPositionHint = null,
                sourceUrl = sourceUrl,
                fetchedAt = fetchedAt
            )
        }
    }

    fun parseHtmlAdSlots(html: String, sourceUrl: String, fetchedAt: Long = System.currentTimeMillis()): List<SponsoredAd> {
        val adLinkRegex = Regex(
            """<a[^>]+(?:data-ad-slot|class=["'][^"']*ad-slot[^"']*["'])[^>]+href=["']([^"']+)["'][^>]*>(.*?)</a>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        return adLinkRegex.findAll(html).mapIndexedNotNull { idx, match ->
            val href = resolveHttpUrl(sourceUrl, match.groupValues[1].trim())
            val title = RssFeedParser.stripHtml(match.groupValues[2]).ifBlank { "Sponsored Feature" }
            if (href.isBlank()) return@mapIndexedNotNull null
            SponsoredAd(
                id = sha256("html_ad|$href|$idx"),
                title = title,
                description = "Sponsored link imported from hashtagfolks.com",
                sponsorName = "HashtagFolks Sponsor",
                imageUrl = null,
                targetUrl = href,
                ctaLabel = "Open",
                slotPositionHint = null,
                sourceUrl = sourceUrl,
                fetchedAt = fetchedAt
            )
        }.toList()
    }

    private fun buildAdCandidateUrls(rawUrl: String): List<String> {
        val normalized = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            rawUrl.trim()
        } else {
            "https://${rawUrl.trim()}"
        }
        val candidates = linkedSetOf(normalized)
        if (normalized.contains("hashtagfolks.com", ignoreCase = true)) {
            val root = "https://hashtagfolks.com"
            candidates.add("$root/ads.json")
            candidates.add("$root/feed/ads.json")
            candidates.add("$root/api/ads")
            candidates.add("$root/wp-json/hashtagfolks/v1/ads")
            candidates.add("$root/ads/feed")
            candidates.add("$root/category/sponsored/feed")
            candidates.add(root)
        }
        return candidates.toList()
    }

    private fun resolveHttpUrl(baseUrl: String, candidate: String): String {
        val trimmed = candidate.trim()
        if (trimmed.isBlank()) return ""
        val resolved = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("//") -> "https:$trimmed"
            else -> try {
                java.net.URI(baseUrl).resolve(trimmed).toString()
            } catch (_: Exception) {
                ""
            }
        }
        return if (resolved.startsWith("http://") || resolved.startsWith("https://")) resolved else ""
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }.take(24)
    }
}
