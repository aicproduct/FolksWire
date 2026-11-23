package com.example.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WordPressPostDto(
    @Json(name = "id") val id: Long = 0L,
    @Json(name = "link") val link: String = "",
    @Json(name = "date_gmt") val dateGmt: String? = null,
    @Json(name = "date") val date: String? = null,
    @Json(name = "title") val title: RenderedFieldDto? = null,
    @Json(name = "excerpt") val excerpt: RenderedFieldDto? = null,
    @Json(name = "content") val content: RenderedFieldDto? = null,
    @Json(name = "_embedded") val embedded: WordPressEmbeddedDto? = null
)

@JsonClass(generateAdapter = true)
data class RenderedFieldDto(
    @Json(name = "rendered") val rendered: String = ""
)

@JsonClass(generateAdapter = true)
data class WordPressEmbeddedDto(
    @Json(name = "author") val authors: List<WordPressAuthorDto>? = null,
    @Json(name = "wp:featuredmedia") val featuredMedia: List<WordPressMediaDto>? = null,
    @Json(name = "wp:term") val terms: List<List<WordPressTermDto>>? = null
)

@JsonClass(generateAdapter = true)
data class WordPressAuthorDto(
    @Json(name = "name") val name: String? = null
)

@JsonClass(generateAdapter = true)
data class WordPressMediaDto(
    @Json(name = "source_url") val sourceUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class WordPressTermDto(
    @Json(name = "name") val name: String? = null
)

@JsonClass(generateAdapter = true)
data class JsonFeedDto(
    @Json(name = "version") val version: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "home_page_url") val homePageUrl: String? = null,
    @Json(name = "items") val items: List<JsonFeedItemDto> = emptyList()
)

@JsonClass(generateAdapter = true)
data class JsonFeedItemDto(
    @Json(name = "id") val id: String = "",
    @Json(name = "url") val url: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "summary") val summary: String? = null,
    @Json(name = "content_html") val contentHtml: String? = null,
    @Json(name = "content_text") val contentText: String? = null,
    @Json(name = "image") val image: String? = null,
    @Json(name = "date_published") val datePublished: String? = null,
    @Json(name = "tags") val tags: List<String>? = null,
    @Json(name = "authors") val authors: List<JsonFeedAuthorDto>? = null
)

@JsonClass(generateAdapter = true)
data class JsonFeedAuthorDto(
    @Json(name = "name") val name: String? = null
)
