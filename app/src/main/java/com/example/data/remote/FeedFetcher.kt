package com.example.data.remote

import com.example.data.model.FeedSource
import com.example.data.model.NewsArticle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

sealed class FeedFetchResult {
    data class Success(val articles: List<NewsArticle>, val resolvedUrl: String) : FeedFetchResult()
    data class Failure(val message: String) : FeedFetchResult()
}

class FeedFetcher(
    private val apiService: NewsFeedApiService = NewsFeedApiService.create()
) {
    suspend fun fetchSource(source: FeedSource): FeedFetchResult = withContext(Dispatchers.IO) {
        val candidates = buildCandidateUrls(source.url)
        var lastFailureMessage = "Could not connect to ${source.name}"

        for (candidateUrl in candidates) {
            when (val attempt = fetchSingleUrl(candidateUrl, source)) {
                is SingleAttempt.Parsed -> {
                    if (attempt.articles.isNotEmpty()) {
                        return@withContext FeedFetchResult.Success(attempt.articles, candidateUrl)
                    }
                    lastFailureMessage = "Feed at $candidateUrl contained no stories"
                }
                is SingleAttempt.HtmlPage -> {
                    val discoveredUrl = RssFeedParser.discoverAlternateFeedUrl(attempt.body, candidateUrl)
                    if (!discoveredUrl.isNullOrBlank() && discoveredUrl != candidateUrl) {
                        val discoveredAttempt = fetchSingleUrl(discoveredUrl, source)
                        if (discoveredAttempt is SingleAttempt.Parsed && discoveredAttempt.articles.isNotEmpty()) {
                            return@withContext FeedFetchResult.Success(discoveredAttempt.articles, discoveredUrl)
                        }
                    }
                    lastFailureMessage = "No RSS/Atom feed found at $candidateUrl"
                }
                is SingleAttempt.HttpError -> {
                    lastFailureMessage = attempt.reason
                    if (attempt.fatalHostError) {
                        return@withContext FeedFetchResult.Failure(lastFailureMessage)
                    }
                }
            }
        }
        FeedFetchResult.Failure(lastFailureMessage)
    }

    private fun buildCandidateUrls(rawUrl: String): List<String> {
        val normalized = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) {
            rawUrl.trim()
        } else {
            "https://${rawUrl.trim()}"
        }
        val list = linkedSetOf(normalized)
        val lower = normalized.lowercase()
        val looksLikeDirectFeed = lower.endsWith("/rss") ||
            lower.endsWith("/feed") ||
            lower.endsWith(".xml") ||
            lower.endsWith(".rss") ||
            lower.endsWith(".json") ||
            lower.contains("wp-json")

        if (!looksLikeDirectFeed) {
            val base = normalized.trimEnd('/')
            list.add("$base/feed")
            list.add("$base/rss")
            list.add("$base/feed.xml")
            list.add("$base/rss.xml")
            list.add("$base/atom.xml")
            list.add("$base/feed.json")
            list.add("$base/wp-json/wp/v2/posts?_embed=1&per_page=20")
        } else if (lower.contains("hashtagfolks.com")) {
            val root = normalized.substringBefore("/feed").substringBefore("/rss").trimEnd('/')
            list.add("$root/rss")
            list.add("$root/feed.xml")
            list.add("$root/rss.xml")
            list.add("$root/atom.xml")
            list.add("$root/feed.json")
            list.add("$root/wp-json/wp/v2/posts?_embed=1&per_page=20")
            list.add(root)
        }
        return list.toList()
    }

    private suspend fun fetchSingleUrl(url: String, source: FeedSource): SingleAttempt {
        val lower = url.lowercase()
        return try {
            // Use Retrofit + MoshiConverterFactory directly for WordPress REST and JSON Feed endpoints
            if (lower.contains("wp-json/wp/v2/posts")) {
                val wpResponse = apiService.fetchWordPressPosts(url)
                if (!wpResponse.isSuccessful) {
                    return SingleAttempt.HttpError("HTTP ${wpResponse.code()} from $url", fatalHostError = false)
                }
                val articles = RssFeedParser.mapWordPressPosts(wpResponse.body().orEmpty(), source)
                return SingleAttempt.Parsed(articles)
            }

            if (lower.endsWith("/feed.json") || lower.endsWith(".json")) {
                val jsonFeedResponse = apiService.fetchJsonFeed(url)
                if (!jsonFeedResponse.isSuccessful) {
                    return SingleAttempt.HttpError("HTTP ${jsonFeedResponse.code()} from $url", fatalHostError = false)
                }
                val articles = jsonFeedResponse.body()?.let { RssFeedParser.mapJsonFeed(it, source) }.orEmpty()
                return SingleAttempt.Parsed(articles)
            }

            val response = apiService.fetchRawFeed(url)
            if (!response.isSuccessful) {
                return SingleAttempt.HttpError("HTTP ${response.code()} from $url", fatalHostError = false)
            }
            val body = response.body()?.string().orEmpty()
            if (body.isBlank()) {
                return SingleAttempt.HttpError("Empty response from $url", fatalHostError = false)
            }
            val trimmed = body.trimStart()
            val contentType = response.headers()["Content-Type"].orEmpty().lowercase()

            if (trimmed.startsWith("<") &&
                (trimmed.contains("<rss", ignoreCase = true) ||
                    trimmed.contains("<feed", ignoreCase = true) ||
                    trimmed.contains("<rdf:RDF", ignoreCase = true) ||
                    trimmed.contains("<channel", ignoreCase = true))
            ) {
                val xmlArticles = RssFeedParser.parseXmlFeed(trimmed, source)
                return SingleAttempt.Parsed(xmlArticles)
            }

            if (contentType.contains("html") || trimmed.contains("<html", ignoreCase = true)) {
                return SingleAttempt.HtmlPage(trimmed)
            }

            val fallbackParsed = RssFeedParser.parseXmlFeed(trimmed, source)
            SingleAttempt.Parsed(fallbackParsed)
        } catch (e: IOException) {
            val msg = e.message ?: "Network error"
            val hostMissing = msg.contains("Unable to resolve host", ignoreCase = true) ||
                msg.contains("NXDOMAIN", ignoreCase = true)
            val cleanMsg = if (hostMissing) {
                "Host unreachable (${source.url.substringAfter("://").substringBefore("/")})"
            } else {
                "Connection error: $msg"
            }
            SingleAttempt.HttpError(cleanMsg, fatalHostError = hostMissing)
        } catch (e: Exception) {
            SingleAttempt.HttpError("Parse error: ${e.message ?: "Unknown"}", fatalHostError = false)
        }
    }

    private sealed class SingleAttempt {
        data class Parsed(val articles: List<NewsArticle>) : SingleAttempt()
        data class HtmlPage(val body: String) : SingleAttempt()
        data class HttpError(val reason: String, val fatalHostError: Boolean) : SingleAttempt()
    }
}
