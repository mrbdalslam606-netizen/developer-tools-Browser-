package com.unixshells.devbrowser

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Durable browser metadata. WebView instances and renderer state are never stored. */
class SessionRepository(context: Context) {
    data class TabSnapshot(
        val stableTabId: Int,
        val url: String,
        val title: String,
        val scrollX: Int,
        val scrollY: Int
    )

    data class SessionSnapshot(
        val activeTabId: Int?,
        val desktopMode: Boolean,
        val tabs: List<TabSnapshot>
    )

    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun save(snapshot: SessionSnapshot) {
        val tabs = JSONArray()
        snapshot.tabs.forEach { tab ->
            tabs.put(JSONObject().apply {
                put("stableTabId", tab.stableTabId)
                put("url", tab.url)
                put("title", tab.title)
                put("scrollX", tab.scrollX)
                put("scrollY", tab.scrollY)
            })
        }
        val root = JSONObject().apply {
            put("schemaVersion", SCHEMA_VERSION)
            put("activeTabId", snapshot.activeTabId ?: JSONObject.NULL)
            put("desktopMode", snapshot.desktopMode)
            put("tabs", tabs)
        }
        preferences.edit().putString(KEY_SESSION, root.toString()).apply()
    }

    fun load(): SessionSnapshot? {
        val raw = preferences.getString(KEY_SESSION, null) ?: return null
        return runCatching {
            val root = JSONObject(raw)
            if (root.optInt("schemaVersion", -1) != SCHEMA_VERSION) return null
            val active = if (root.isNull("activeTabId")) null else root.optInt("activeTabId")
            val tabsJson = root.optJSONArray("tabs") ?: JSONArray()
            val tabs = buildList {
                for (index in 0 until tabsJson.length()) {
                    val tab = tabsJson.optJSONObject(index) ?: continue
                    val url = tab.optString("url", "about:blank")
                    if (url.isBlank()) continue
                    add(TabSnapshot(
                        stableTabId = tab.optInt("stableTabId", index),
                        url = url,
                        title = tab.optString("title", "New Tab"),
                        scrollX = tab.optInt("scrollX", 0),
                        scrollY = tab.optInt("scrollY", 0)
                    ))
                }
            }
            if (tabs.isEmpty()) null else SessionSnapshot(
                activeTabId = active,
                desktopMode = root.optBoolean("desktopMode", true),
                tabs = tabs
            )
        }.getOrNull()
    }

    fun clear() {
        preferences.edit().remove(KEY_SESSION).apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "devbrowser_session"
        private const val KEY_SESSION = "snapshot"
        private const val SCHEMA_VERSION = 1
    }
}
