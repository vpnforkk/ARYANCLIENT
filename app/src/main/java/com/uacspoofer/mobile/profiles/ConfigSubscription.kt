package com.uacspoofer.mobile.profiles

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID

object ConfigSubscription {

    const val SUBSCRIPTION_URL =
        "https://narmafzar090.github.io/my-sub/sub.txt"

    suspend fun fetchProfiles(): Result<List<ProxyProfile>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = URL(
                    "$SUBSCRIPTION_URL?ts=${System.currentTimeMillis()}"
                )

                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 15_000
                    readTimeout = 20_000
                    useCaches = false
                    setRequestProperty(
                        "Cache-Control",
                        "no-cache"
                    )
                    setRequestProperty(
                        "Pragma",
                        "no-cache"
                    )
                    setRequestProperty(
                        "User-Agent",
                        "AryanClient/1.0"
                    )
                }

                try {
                    if (connection.responseCode !in 200..299) {
                        error(
                            "Subscription HTTP ${connection.responseCode}"
                        )
                    }

                    val text = connection.inputStream
                        .bufferedReader(StandardCharsets.UTF_8)
                        .use { it.readText() }

                    parseProfiles(text)
                } finally {
                    connection.disconnect()
                }
            }
        }

    private fun parseProfiles(text: String): List<ProxyProfile> {
        val uris = ProfileUriParser.extractUris(text)

        if (uris.isEmpty()) {
            error("No supported configurations found")
        }

        val result = mutableListOf<ProxyProfile>()
        val seen = HashSet<String>()

        uris.forEach { raw ->
            val clean = raw
                .trim()
                .replace("&amp;", "&")

            if (clean.isBlank()) return@forEach

            val key = clean.lowercase()

            if (!seen.add(key)) {
                return@forEach
            }

            runCatching {
                val stableId = subscriptionId(clean)

                ProfileUriParser.parse(
                    raw = clean,
                    id = stableId,
                )
            }.onSuccess { profile ->
                result += profile
            }
        }

        if (result.isEmpty()) {
            error("No valid VLESS, VMess or Trojan configurations found")
        }

        return result
    }

    private fun subscriptionId(uri: String): String {
        val uuid = UUID.nameUUIDFromBytes(
            uri.toByteArray(StandardCharsets.UTF_8)
        )

        return "subscription:$uuid"
    }
}
