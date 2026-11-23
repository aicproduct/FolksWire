package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.ReaderFontScale
import com.example.data.local.ThemeMode
import com.example.data.local.UserPreferences
import com.example.data.model.FeedSource
import com.example.data.model.SponsoredAd
import com.example.ui.components.EditAdUrlDialog
import com.example.ui.components.EditSourceUrlDialog
import com.example.ui.components.ProviderBadge
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusWarning
import com.example.ui.util.TimeFormatters
import com.example.ui.viewmodel.AdImportStatus

@Composable
fun SourcesScreen(
    sources: List<FeedSource>,
    importedAds: List<SponsoredAd>,
    adImportStatus: AdImportStatus,
    preferences: UserPreferences,
    onToggleSource: (String, Boolean) -> Unit,
    onRefreshSource: (String) -> Unit,
    onUpdateSourceUrl: (String, String) -> Unit,
    onAddCustomSource: (String, String, String) -> Unit,
    onDeleteCustomSource: (String) -> Unit,
    onToggleAdsEnabled: (Boolean) -> Unit,
    onRefreshAds: () -> Unit,
    onUpdateAdFeedUrl: (String) -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetFontScale: (ReaderFontScale) -> Unit,
    onClearCache: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditAdDialog by remember { mutableStateOf(false) }
    var editingSource by remember { mutableStateOf<FeedSource?>(null) }

    if (showEditAdDialog) {
        EditAdUrlDialog(
            currentUrl = preferences.adFeedUrl,
            onDismiss = { showEditAdDialog = false },
            onConfirm = { newUrl ->
                onUpdateAdFeedUrl(newUrl)
                showEditAdDialog = false
            }
        )
    }

    if (showAddDialog) {
        AddCustomFeedDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { name, url, cat ->
                onAddCustomSource(name, url, cat)
                showAddDialog = false
            }
        )
    }

    editingSource?.let { src ->
        EditSourceUrlDialog(
            source = src,
            onDismiss = { editingSource = null },
            onConfirm = { newUrl ->
                onUpdateSourceUrl(src.id, newUrl)
                editingSource = null
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Column {
                Text(
                    text = "WIRE SOURCES",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "${sources.count { it.isEnabled }} of ${sources.size} feeds active",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = { showAddDialog = true },
                modifier = Modifier.testTag("add_custom_feed_button")
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Feed")
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)

        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 760.dp)
            ) {
                items(sources, key = { it.id }) { source ->
                    SourceCard(
                        source = source,
                        onToggle = { enabled -> onToggleSource(source.id, enabled) },
                        onRefresh = { onRefreshSource(source.id) },
                        onEditUrl = { editingSource = source },
                        onDelete = if (!source.isDefault) {
                            { onDeleteCustomSource(source.id) }
                        } else {
                            null
                        }
                    )
                }

                item(key = "ad_slot_config") {
                    AdSlotConfigCard(
                        adsEnabled = preferences.adsEnabled,
                        adFeedUrl = preferences.adFeedUrl,
                        importedCount = importedAds.size,
                        adImportStatus = adImportStatus,
                        onToggleAds = onToggleAdsEnabled,
                        onRefreshAds = onRefreshAds,
                        onEditAdUrl = { showEditAdDialog = true }
                    )
                }

                item(key = "reader_preferences") {
                    Spacer(modifier = Modifier.height(8.dp))
                    ReaderPreferencesSection(
                        preferences = preferences,
                        onSetThemeMode = onSetThemeMode,
                        onSetFontScale = onSetFontScale,
                        onClearCache = onClearCache
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceCard(
    source: FeedSource,
    onToggle: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onEditUrl: () -> Unit,
    onDelete: (() -> Unit)?
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("source_card_${source.id}")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    ProviderBadge(
                        providerGroup = source.providerGroup,
                        sourceName = source.category
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = source.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(
                    checked = source.isEnabled,
                    onCheckedChange = onToggle,
                    modifier = Modifier.testTag("source_switch_${source.id}")
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = source.url,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    val statusColor = when {
                        !source.isEnabled -> MaterialTheme.colorScheme.onSurfaceVariant
                        source.lastStatusOk -> StatusSuccess
                        else -> StatusWarning
                    }
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    val statusText = when {
                        !source.isEnabled -> "Paused"
                        !source.lastStatusOk && !source.lastError.isNullOrBlank() -> source.lastError
                        source.lastSyncedAt != null -> "Synced ${TimeFormatters.relativeTime(source.lastSyncedAt)}"
                        else -> "Ready to sync"
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row {
                    IconButton(
                        onClick = onEditUrl,
                        modifier = Modifier.testTag("source_edit_${source.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit feed URL"
                        )
                    }
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.testTag("source_refresh_${source.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Refresh source"
                        )
                    }
                    if (onDelete != null) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.testTag("source_delete_${source.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = "Delete custom source",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReaderPreferencesSection(
    preferences: UserPreferences,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetFontScale: (ReaderFontScale) -> Unit,
    onClearCache: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "READER PREFERENCES",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Paper Tone",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { index, mode ->
                        SegmentedButton(
                            selected = preferences.themeMode == mode,
                            onClick = { onSetThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = ThemeMode.entries.size
                            ),
                            modifier = Modifier.testTag("theme_mode_${mode.name.lowercase()}")
                        ) {
                            Text(mode.label, maxLines = 1)
                        }
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Article Text Size",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    ReaderFontScale.entries.forEachIndexed { index, scale ->
                        SegmentedButton(
                            selected = preferences.fontScale == scale,
                            onClick = { onSetFontScale(scale) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = ReaderFontScale.entries.size
                            ),
                            modifier = Modifier.testTag("font_scale_${scale.name.lowercase()}")
                        ) {
                            Text(scale.label, maxLines = 1)
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = onClearCache,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("clear_cache_button")
            ) {
                Text("Clear Cached Feed Stories (Keep Bookmarks)")
            }
        }
    }
}

@Composable
private fun AddCustomFeedDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String, category: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Tech") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_feed_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Source Name") },
                    placeholder = { Text("e.g. HashtagFolks Tech") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_feed_name_input")
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("RSS / Atom / Site URL") },
                    placeholder = { Text("https://hashtagfolks.com/feed") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_feed_url_input")
                )
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category Tag") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_feed_category_input")
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank() && url.isNotBlank()) {
                        onConfirm(name, url, category)
                    }
                },
                modifier = Modifier.testTag("confirm_add_feed_button")
            ) {
                Text("Add & Sync")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun AdSlotConfigCard(
    adsEnabled: Boolean,
    adFeedUrl: String,
    importedCount: Int,
    adImportStatus: AdImportStatus,
    onToggleAds: (Boolean) -> Unit,
    onRefreshAds: () -> Unit,
    onEditAdUrl: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ad_slot_config_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "HASHTAGFOLKS.COM AD SLOTS",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (adsEnabled) "$importedCount sponsored ad(s) cached" else "Ad slots paused",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = adsEnabled,
                    onCheckedChange = onToggleAds,
                    modifier = Modifier.testTag("ads_enabled_switch")
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = adFeedUrl,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (!adImportStatus.lastError.isNullOrBlank() && adsEnabled) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = adImportStatus.lastError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onRefreshAds,
                    enabled = adsEnabled && !adImportStatus.isImporting,
                    modifier = Modifier.testTag("sources_refresh_ads_button")
                ) {
                    Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (adImportStatus.isImporting) "Importing…" else "Import Ads")
                }
                OutlinedButton(
                    onClick = onEditAdUrl,
                    modifier = Modifier.testTag("sources_edit_ad_url_button")
                ) {
                    Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Edit Ad URL")
                }
            }
        }
    }
}
