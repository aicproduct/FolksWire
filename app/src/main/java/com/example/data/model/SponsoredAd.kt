package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sponsored_ads")
data class SponsoredAd(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val sponsorName: String,
    val imageUrl: String?,
    val targetUrl: String,
    val ctaLabel: String,
    val slotPositionHint: Int?,
    val sourceUrl: String,
    val fetchedAt: Long
)
