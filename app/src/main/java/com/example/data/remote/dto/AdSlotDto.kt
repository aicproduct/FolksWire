package com.example.data.remote.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AdFeedResponseDto(
    @Json(name = "ads") val ads: List<AdSlotDto>? = null,
    @Json(name = "items") val items: List<AdSlotDto>? = null,
    @Json(name = "data") val data: List<AdSlotDto>? = null
)

@JsonClass(generateAdapter = true)
data class AdSlotDto(
    @Json(name = "id") val id: String? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "headline") val headline: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "body") val body: String? = null,
    @Json(name = "sponsor") val sponsor: String? = null,
    @Json(name = "advertiser") val advertiser: String? = null,
    @Json(name = "image_url") val imageUrlSnake: String? = null,
    @Json(name = "imageUrl") val imageUrlCamel: String? = null,
    @Json(name = "image") val image: String? = null,
    @Json(name = "cta_text") val ctaTextSnake: String? = null,
    @Json(name = "cta") val cta: String? = null,
    @Json(name = "target_url") val targetUrlSnake: String? = null,
    @Json(name = "targetUrl") val targetUrlCamel: String? = null,
    @Json(name = "url") val url: String? = null,
    @Json(name = "link") val link: String? = null,
    @Json(name = "slot_position") val slotPosition: Int? = null
)
