package com.intagri.mtgleader.persistence.auth

import android.content.Context
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

class PersistentCookieJar(
    context: Context,
    moshi: Moshi,
) : CookieJar {

    companion object {
        private const val PREFS_NAME = "auth_cookies"
        private const val KEY_COOKIES = "cookie_store"
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val adapter = moshi.adapter<List<StoredCookie>>(
        Types.newParameterizedType(List::class.java, StoredCookie::class.java)
    )
    private val cache = loadFromPrefs()

    @Synchronized
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val now = System.currentTimeMillis()
        cookies.forEach { newCookie ->
            cache.removeAll { it.name == newCookie.name && it.domain == newCookie.domain && it.path == newCookie.path }
            if (newCookie.expiresAt > now) {
                cache.add(newCookie)
            }
        }
        persist()
    }

    @Synchronized
    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        var changed = false
        val iterator = cache.iterator()
        while (iterator.hasNext()) {
            val cookie = iterator.next()
            if (cookie.expiresAt <= now) {
                iterator.remove()
                changed = true
            }
        }
        if (changed) {
            persist()
        }
        return cache.filter { it.matches(url) }
    }

    @Synchronized
    fun clear() {
        cache.clear()
        prefs.edit().remove(KEY_COOKIES).apply()
    }

    private fun loadFromPrefs(): MutableList<Cookie> {
        val json = prefs.getString(KEY_COOKIES, null) ?: return mutableListOf()
        return try {
            val stored = adapter.fromJson(json).orEmpty()
            val now = System.currentTimeMillis()
            stored.mapNotNull { it.toCookie() }
                .filter { it.expiresAt > now }
                .toMutableList()
        } catch (e: Exception) {
            prefs.edit().remove(KEY_COOKIES).apply()
            mutableListOf()
        }
    }

    private fun persist() {
        val now = System.currentTimeMillis()
        val stored = cache.filter { it.expiresAt > now }
            .map { StoredCookie.fromCookie(it) }
        prefs.edit().putString(KEY_COOKIES, adapter.toJson(stored)).apply()
    }

    @JsonClass(generateAdapter = true)
    data class StoredCookie(
        val name: String,
        val value: String,
        val expiresAt: Long,
        val domain: String,
        val path: String,
        val secure: Boolean,
        val httpOnly: Boolean,
        val hostOnly: Boolean,
    ) {
        fun toCookie(): Cookie? {
            return try {
                val builder = Cookie.Builder()
                    .name(name)
                    .value(value)
                    .path(path)
                    .expiresAt(expiresAt)
                if (hostOnly) {
                    builder.hostOnlyDomain(domain)
                } else {
                    builder.domain(domain)
                }
                if (secure) {
                    builder.secure()
                }
                if (httpOnly) {
                    builder.httpOnly()
                }
                builder.build()
            } catch (e: IllegalArgumentException) {
                null
            }
        }

        companion object {
            fun fromCookie(cookie: Cookie): StoredCookie {
                return StoredCookie(
                    name = cookie.name,
                    value = cookie.value,
                    expiresAt = cookie.expiresAt,
                    domain = cookie.domain,
                    path = cookie.path,
                    secure = cookie.secure,
                    httpOnly = cookie.httpOnly,
                    hostOnly = cookie.hostOnly,
                )
            }
        }
    }
}
