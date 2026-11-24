package com.example.data.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class WebSearchResult(
    val summaryText: String,
    val sources: List<String>
)

class WebSearchService {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    suspend fun searchWeb(query: String): WebSearchResult = withContext(Dispatchers.IO) {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) {
            return@withContext WebSearchResult("Empty search query.", emptyList())
        }

        val snippets = mutableListOf<String>()
        val sources = mutableListOf<String>()

        // 1. DuckDuckGo Instant Answer API
        runCatching {
            val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
            val ddgReq = Request.Builder()
                .url("https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1")
                .header("User-Agent", "MarcoAI-AndroidAssistant/1.0")
                .build()
            client.newCall(ddgReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    if (body.isNotBlank()) {
                        val json = JSONObject(body)
                        val abstractText = json.optString("AbstractText").trim()
                        val abstractUrl = json.optString("AbstractURL").trim()
                        val answer = json.optString("Answer").trim()
                        if (answer.isNotBlank()) snippets += "Direct Answer: $answer"
                        if (abstractText.isNotBlank()) {
                            snippets += abstractText
                            if (abstractUrl.isNotBlank()) sources += abstractUrl
                        }
                        val related = json.optJSONArray("RelatedTopics")
                        if (related != null && snippets.isEmpty()) {
                            for (i in 0 until minOf(3, related.length())) {
                                val topic = related.optJSONObject(i) ?: continue
                                val text = topic.optString("Text").trim()
                                val firstUrl = topic.optString("FirstURL").trim()
                                if (text.isNotBlank()) snippets += text
                                if (firstUrl.isNotBlank()) sources += firstUrl
                            }
                        }
                    }
                }
            }
        }.onFailure { e ->
            Log.w(TAG, "DuckDuckGo search error: ${e.message}")
        }

        // 2. Wikipedia Search & Extract API (covers encyclopedic & Hindi/English topics richly)
        runCatching {
            val encoded = URLEncoder.encode(cleanQuery, "UTF-8")
            val wikiReq = Request.Builder()
                .url(
                    "https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded" +
                        "&utf8=&format=json&srlimit=3"
                )
                .header("User-Agent", "MarcoAI-AndroidAssistant/1.0")
                .build()
            client.newCall(wikiReq).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string().orEmpty()
                    if (body.isNotBlank()) {
                        val json = JSONObject(body)
                        val searchArray = json.optJSONObject("query")?.optJSONArray("search")
                        if (searchArray != null) {
                            for (i in 0 until minOf(3, searchArray.length())) {
                                val item = searchArray.optJSONObject(i) ?: continue
                                val title = item.optString("title").trim()
                                val rawSnippet = item.optString("snippet")
                                    .replace(Regex("<[^>]*>"), "")
                                    .replace("&quot;", "\"")
                                    .replace("&amp;", "&")
                                    .trim()
                                if (title.isNotBlank() && rawSnippet.isNotBlank()) {
                                    snippets += "$title: $rawSnippet"
                                    val wikiUrl = "https://en.wikipedia.org/wiki/${
                                        URLEncoder.encode(title.replace(" ", "_"), "UTF-8")
                                    }"
                                    sources += wikiUrl
                                }
                            }
                        }
                    }
                }
            }
        }.onFailure { e ->
            Log.w(TAG, "Wikipedia search error: ${e.message}")
        }

        val finalSummary = if (snippets.isEmpty()) {
            "No direct web snippets found for '$cleanQuery'."
        } else {
            snippets.distinct().take(4).joinToString("\n")
        }

        WebSearchResult(
            summaryText = finalSummary,
            sources = sources.distinct().take(3)
        )
    }

    companion object {
        private const val TAG = "WebSearchService"
    }
}
