package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

object ProviderGroup {
    const val HASHTAGFOLKS = "HASHTAGFOLKS"
    const val WIRED = "WIRED"
    const val CUSTOM = "CUSTOM"
}

@Entity(tableName = "feed_sources")
data class FeedSource(
    @PrimaryKey val id: String,
    val name: String,
    val providerGroup: String,
    val category: String,
    val url: String,
    val isEnabled: Boolean = true,
    val isDefault: Boolean = false,
    val lastSyncedAt: Long? = null,
    val lastStatusOk: Boolean = true,
    val lastError: String? = null
)
