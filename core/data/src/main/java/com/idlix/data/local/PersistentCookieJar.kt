package com.idlix.data.local

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Serializable
internal data class StoredCookie(
    val name: String,
    val value: String,
    val expiresAt: Long,
    val domain: String,
    val path: String,
    val secure: Boolean,
    val httpOnly: Boolean,
    val hostOnly: Boolean,
    val persistent: Boolean
) {
    fun toOkHttpCookie(): Cookie {
        val builder = Cookie.Builder()
            .name(name)
            .value(value)
            .expiresAt(expiresAt)
            .path(path)
        if (hostOnly) builder.hostOnlyDomain(domain) else builder.domain(domain)
        if (secure) builder.secure()
        if (httpOnly) builder.httpOnly()
        return builder.build()
    }

    companion object {
        fun fromOkHttpCookie(cookie: Cookie): StoredCookie = StoredCookie(
            name = cookie.name,
            value = cookie.value,
            expiresAt = cookie.expiresAt,
            domain = cookie.domain,
            path = cookie.path,
            secure = cookie.secure,
            httpOnly = cookie.httpOnly,
            hostOnly = cookie.hostOnly,
            persistent = cookie.persistent
        )
    }
}

class PersistentCookieJar : CookieJar {

    private val lock = Any()
    private val cookiesByKey = LinkedHashMap<String, StoredCookie>()

    @Volatile
    private var prefs: SharedPreferences? = null

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    fun init(context: Context) {
        synchronized(lock) {
            if (prefs == null) {
                val p = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                prefs = p
                loadFromPrefsLocked(p)
            }
        }
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (cookies.isEmpty()) return
        val now = System.currentTimeMillis()
        synchronized(lock) {
            for (cookie in cookies) {
                val key = cookieKey(cookie.domain, cookie.path, cookie.name)
                if (cookie.expiresAt <= now || cookie.value.isEmpty() && cookie.expiresAt <= now) {
                    cookiesByKey.remove(key)
                } else if (cookie.expiresAt <= now) {
                    cookiesByKey.remove(key)
                } else {
                    cookiesByKey[key] = StoredCookie.fromOkHttpCookie(cookie)
                }
            }
            pruneExpiredLocked(now)
            persistLocked()
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            val pruned = pruneExpiredLocked(now)
            if (pruned) {
                persistLocked()
            }
            val matching = ArrayList<Cookie>()
            for (stored in cookiesByKey.values) {
                val okCookie = stored.toOkHttpCookie()
                if (okCookie.matches(url)) {
                    matching.add(okCookie)
                }
            }
            return matching
        }
    }

    fun upsertCookie(urlString: String, name: String, value: String) {
        val url = urlString.toHttpUrlOrNull() ?: return
        upsertCookie(url, name, value)
    }

    fun upsertCookie(url: HttpUrl, name: String, value: String) {
        if (name.isBlank() || value.isBlank()) return
        val cookie = Cookie.Builder()
            .hostOnlyDomain(url.host)
            .path("/")
            .name(name)
            .value(value)
            .apply { if (url.isHttps) secure() }
            .build()
        synchronized(lock) {
            cookiesByKey[cookieKey(cookie.domain, cookie.path, cookie.name)] = StoredCookie.fromOkHttpCookie(cookie)
            persistLocked()
        }
    }

    fun ensureCookieIfMissing(url: HttpUrl, name: String, value: String) {
        if (name.isBlank() || value.isBlank()) return
        synchronized(lock) {
            val exists = cookiesByKey.values.any { stored ->
                stored.name == name && stored.toOkHttpCookie().matches(url)
            }
            if (!exists) {
                val cookie = Cookie.Builder()
                    .hostOnlyDomain(url.host)
                    .path("/")
                    .name(name)
                    .value(value)
                    .apply { if (url.isHttps) secure() }
                    .build()
                cookiesByKey[cookieKey(cookie.domain, cookie.path, cookie.name)] = StoredCookie.fromOkHttpCookie(cookie)
                persistLocked()
            }
        }
    }

    fun hasAuthCookies(url: HttpUrl): Boolean {
        val now = System.currentTimeMillis()
        synchronized(lock) {
            pruneExpiredLocked(now)
            return cookiesByKey.values.any { stored ->
                val ok = stored.toOkHttpCookie()
                ok.matches(url) && !PRESERVED_NON_AUTH_COOKIES.contains(stored.name.lowercase())
            }
        }
    }

    fun cookieSnapshotForUrl(url: HttpUrl): String {
        return loadForRequest(url)
            .sortedBy { it.name }
            .joinToString("; ") { "${it.name}=${it.value}" }
    }

    fun clearAuthCookies() {
        synchronized(lock) {
            val iterator = cookiesByKey.entries.iterator()
            var changed = false
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (!PRESERVED_NON_AUTH_COOKIES.contains(entry.value.name.lowercase())) {
                    iterator.remove()
                    changed = true
                }
            }
            if (changed) {
                persistLocked()
            }
        }
    }

    fun clearAll() {
        synchronized(lock) {
            cookiesByKey.clear()
            persistLocked()
        }
    }

    private fun pruneExpiredLocked(now: Long): Boolean {
        var removed = false
        val iterator = cookiesByKey.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.expiresAt <= now) {
                iterator.remove()
                removed = true
            }
        }
        return removed
    }

    private fun loadFromPrefsLocked(p: SharedPreferences) {
        val raw = p.getString(KEY_COOKIES_JSON, null) ?: return
        try {
            val list = json.decodeFromString<List<StoredCookie>>(raw)
            val now = System.currentTimeMillis()
            cookiesByKey.clear()
            for (item in list) {
                if (item.expiresAt > now) {
                    cookiesByKey[cookieKey(item.domain, item.path, item.name)] = item
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun persistLocked() {
        val p = prefs ?: return
        try {
            val encoded = json.encodeToString(cookiesByKey.values.toList())
            p.edit().putString(KEY_COOKIES_JSON, encoded).apply()
        } catch (_: Exception) {
        }
    }

    private fun cookieKey(domain: String, path: String, name: String): String =
        "${domain.lowercase()}|$path|$name"

    companion object {
        private const val PREFS_NAME = "idlix_cookies"
        private const val KEY_COOKIES_JSON = "cookies_json"
        private val PRESERVED_NON_AUTH_COOKIES = setOf("did", "cf_clearance", "__cf_bm")
    }
}
