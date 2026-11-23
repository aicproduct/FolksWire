package com.example.ui.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.example.data.model.NewsArticle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeFormatters {
    fun relativeTime(timestampMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        val diff = (nowMillis - timestampMillis).coerceAtLeast(0L)
        val minutes = diff / 60_000L
        val hours = diff / 3_600_000L
        val days = diff / 86_400_000L
        return when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            hours < 24 -> "${hours}h ago"
            days < 7 -> "${days}d ago"
            else -> SimpleDateFormat("MMM d", Locale.US).format(Date(timestampMillis))
        }
    }

    fun fullDate(timestampMillis: Long): String {
        return SimpleDateFormat("EEE, MMM d, yyyy • h:mm a", Locale.US).format(Date(timestampMillis))
    }

    fun estimateReadTime(article: NewsArticle): String {
        val words = (article.contentHtml.ifBlank { article.summary })
            .replace(Regex("<[^>]+>"), " ")
            .trim()
            .split(Regex("\\s+"))
            .size
        val mins = (words / 180).coerceAtLeast(1)
        return "$mins min read"
    }
}

fun shareArticle(context: Context, article: NewsArticle) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, article.title)
        putExtra(Intent.EXTRA_TEXT, "${article.title}\n\nVia ${article.sourceName}\n${article.link}")
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share story"))
}

fun openInExternalBrowser(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (_: Exception) {
    }
}
