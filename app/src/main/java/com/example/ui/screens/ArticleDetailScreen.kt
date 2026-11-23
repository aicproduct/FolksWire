package com.example.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.ReaderFontScale
import com.example.data.model.NewsArticle
import com.example.data.remote.RssFeedParser
import com.example.ui.components.HashtagChip
import com.example.ui.components.ProviderBadge
import com.example.ui.util.TimeFormatters
import com.example.ui.util.openInExternalBrowser
import com.example.ui.util.shareArticle

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ArticleDetailScreen(
    article: NewsArticle?,
    fontScale: ReaderFontScale,
    onBack: () -> Unit,
    onMarkAsRead: (String) -> Unit,
    onToggleBookmark: (NewsArticle) -> Unit,
    onCycleFontScale: (ReaderFontScale) -> Unit,
    onHashtagClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showWebView by remember { mutableStateOf(false) }
    var webViewLoading by remember { mutableStateOf(false) }

    LaunchedEffect(article?.id) {
        if (article != null && !article.isRead) {
            onMarkAsRead(article.id)
        }
    }

    BackHandler(enabled = showWebView) {
        showWebView = false
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("detail_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
                        )
                    }
                },
                title = {
                    if (article != null) {
                        ProviderBadge(
                            providerGroup = article.providerGroup,
                            sourceName = article.sourceName
                        )
                    }
                },
                actions = {
                    if (article != null) {
                        IconButton(
                            onClick = {
                                val entries = ReaderFontScale.entries
                                val next = entries[(entries.indexOf(fontScale) + 1) % entries.size]
                                onCycleFontScale(next)
                            },
                            modifier = Modifier.testTag("detail_font_scale_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.TextFields,
                                contentDescription = "Cycle text size (${fontScale.label})"
                            )
                        }
                        IconButton(
                            onClick = { showWebView = !showWebView },
                            modifier = Modifier.testTag("detail_toggle_web_button")
                        ) {
                            Icon(
                                imageVector = if (showWebView) Icons.AutoMirrored.Outlined.Article else Icons.Outlined.Language,
                                contentDescription = stringResource(
                                    if (showWebView) R.string.action_toggle_reader else R.string.action_toggle_webview
                                )
                            )
                        }
                        IconButton(
                            onClick = { onToggleBookmark(article) },
                            modifier = Modifier.testTag("detail_bookmark_button")
                        ) {
                            Icon(
                                imageVector = if (article.isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = stringResource(
                                    if (article.isBookmarked) R.string.action_unbookmark else R.string.action_bookmark
                                ),
                                tint = if (article.isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = { shareArticle(context, article) },
                            modifier = Modifier.testTag("detail_share_button")
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = stringResource(R.string.action_share)
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (article == null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Text(
                    text = "Story not found in local cache.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        if (showWebView) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (webViewLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                SafeArticleWebView(
                    url = article.link,
                    onLoadingChanged = { webViewLoading = it },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            val paragraphs = remember(article.id, article.contentHtml, article.summary) {
                val parsed = RssFeedParser.htmlToReadableParagraphs(article.contentHtml)
                if (parsed.isNotEmpty()) parsed else listOf(article.summary).filter { it.isNotBlank() }
            }

            Box(
                contentAlignment = Alignment.TopCenter,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 720.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = article.category.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = article.title,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontSize = (28 * fontScale.multiplier).sp,
                            lineHeight = (35 * fontScale.multiplier).sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "By ${article.author} • ${TimeFormatters.fullDate(article.publishedAt)} • ${TimeFormatters.estimateReadTime(article)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (!article.imageUrl.isNullOrBlank()) {
                        AsyncImage(
                            model = article.imageUrl,
                            fallback = painterResource(R.drawable.img_editorial_fallback),
                            error = painterResource(R.drawable.img_editorial_fallback),
                            contentDescription = article.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(14.dp))
                        )
                        Spacer(modifier = Modifier.height(18.dp))
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(18.dp))

                    paragraphs.forEach { para ->
                        Text(
                            text = para,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = (17 * fontScale.multiplier).sp,
                                lineHeight = (27 * fontScale.multiplier).sp
                            ),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    if (article.hashtags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            article.hashtags.forEach { tag ->
                                HashtagChip(
                                    tag = tag,
                                    onClick = { onHashtagClick(tag) }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { showWebView = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("read_full_in_app_button")
                        ) {
                            Icon(Icons.Outlined.Language, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("In-App Web")
                        }
                        OutlinedButton(
                            onClick = { openInExternalBrowser(context, article.link) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("open_external_browser_button")
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Browser")
                        }
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun SafeArticleWebView(
    url: String,
    onLoadingChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val scheme = request?.url?.scheme?.lowercase()
                        return scheme != "http" && scheme != "https"
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        onLoadingChanged(true)
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        onLoadingChanged(false)
                    }
                }
                loadUrl(url)
            }
        }
    )
}
