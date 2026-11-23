package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "articles",
    indices = [
        Index(value = ["sourceId"]),
        Index(value = ["publishedAt"]),
        Index(value = ["isBookmarked"])
    ]
)
data class NewsArticle(
    @PrimaryKey val id: String,
    val sourceId: String,
    val sourceName: String,
    val providerGroup: String,
    val category: String,
    val title: String,
    val summary: String,
    val contentHtml: String,
    val author: String,
    val link: String,
    val imageUrl: String?,
    val hashtagsCsv: String,
    val publishedAt: Long,
    val fetchedAt: Long,
    val isBookmarked: Boolean = false,
    val isRead: Boolean = false
) {
    val hashtags: List<String>
        get() = if (hashtagsCsv.isBlank()) {
            emptyList()
        } else {
            hashtagsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        }
}
