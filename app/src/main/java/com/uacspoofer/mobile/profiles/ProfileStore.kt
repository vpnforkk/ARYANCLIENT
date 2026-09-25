package com.uacspoofer.mobile.profiles

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

data class ProfileImportResult(
    val library: ProfileLibrary,
    val importedCount: Int,
    val errors: List<String>,
)

class ProfileStore(context: Context) {

    private val appContext = context.applicationContext

    private val prefs =
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val refreshExecutor =
        Executors.newSingleThreadExecutor()

    private val mainHandler =
        Handler(Looper.getMainLooper())

    /**
     * آدرس سابسکریپشن آریان کلاینت
     *
     * برای تغییر کانفیگ‌ها فقط همین فایل sub.txt را در GitHub
     * تغییر بده.
     */
    private val subscriptionUrl =
        "https://narmafzar090.github.io/my-sub/sub.txt"

    /**
     * حداقل فاصله بین دو بار دریافت ساب
     * 60 ثانیه
     */
    private val refreshIntervalMs = 60_000L

    @Synchronized
    fun snapshot(): ProfileLibrary {
        migrateLegacyOnce()

        val profiles = readProfiles()

        val requested =
            prefs.getString(
                KEY_SELECTED,
                ProxyProfile.BUILT_IN_ID
            ) ?: ProxyProfile.BUILT_IN_ID

        val selected =
            requested.takeIf { id ->
                ProxyProfile.isProtectedBuiltIn(id) ||
                    profiles.any { it.id == id }
            } ?: ProxyProfile.BUILT_IN_ID

        if (selected != requested) {
            prefs.edit()
                .putString(KEY_SELECTED, selected)
                .apply()
        }

        return ProfileLibrary(
            profiles,
            selected
        )
    }

    /**
     * دریافت سابسکریپشن در پس‌زمینه.
     *
     * این تابع UI را قفل نمی‌کند.
     *
     * بعد از دریافت موفق، callback با Library جدید اجرا می‌شود.
     */
    fun refreshRemoteSubscription(
        force: Boolean = false,
        onResult: ((ProfileLibrary) -> Unit)? = null,
    ) {
        val now = System.currentTimeMillis()

        val lastRefresh =
            prefs.getLong(KEY_LAST_SUB_REFRESH, 0L)

        if (!force &&
            now - lastRefresh < refreshIntervalMs
        ) {
            onResult?.let { callback ->
                mainHandler.post {
                    callback(snapshot())
                }
            }
            return
        }

        refreshExecutor.execute {
            val result = runCatching {
                downloadSubscription()
            }

            result.onSuccess { text ->
                val updated =
                    synchronized(this) {
                        updateSubscriptionProfiles(text)
                    }

                prefs.edit()
                    .putLong(
                        KEY_LAST_SUB_REFRESH,
                        System.currentTimeMillis()
                    )
                    .apply()

                mainHandler.post {
                    onResult?.invoke(updated)
                }
            }

            result.onFailure {
                /*
                 * اگر اینترنت یا GitHub در دسترس نبود،
                 * کانفیگ‌های ذخیره‌شده قبلی باقی می‌مانند.
                 */
                mainHandler.post {
                    onResult?.invoke(snapshot())
                }
            }
        }
    }

    /**
     * دانلود فایل sub.txt
     */
    private fun downloadSubscription(): String {
        val connection =
            (URL(subscriptionUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 15_000
                useCaches = false
                setRequestProperty(
                    "Cache-Control",
                    "no-cache"
                )
                setRequestProperty(
                    "User-Agent",
                    "AryanClient/1.0"
                )
            }

        try {
            val responseCode =
                connection.responseCode

            require(responseCode in 200..299) {
                "Subscription HTTP error: $responseCode"
            }

            val text =
                connection.inputStream
                    .bufferedReader(Charsets.UTF_8)
                    .use { it.readText() }
                    .trim()

            require(text.isNotBlank()) {
                "Subscription is empty"
            }

            return decodeSubscriptionIfNeeded(text)
        } finally {
            connection.disconnect()
        }
    }

    /**
     * بعضی ساب‌ها متن عادی هستند،
     * بعضی Base64 هستند.
     *
     * ابتدا متن معمولی بررسی می‌شود.
     * اگر لینک قابل تشخیص نبود، Base64 امتحان می‌شود.
     */
    private fun decodeSubscriptionIfNeeded(
        text: String
    ): String {

        val normalized =
            text.trim()

        if (
            normalized.contains(
                "vless://",
                ignoreCase = true
            ) ||
            normalized.contains(
                "vmess://",
                ignoreCase = true
            ) ||
            normalized.contains(
                "trojan://",
                ignoreCase = true
            )
        ) {
            return normalized
        }

        val compact =
            normalized
                .replace("\\s".toRegex(), "")

        val decoded =
            runCatching {
                Base64.decode(
                    compact,
                    Base64.DEFAULT or Base64.NO_WRAP
                ).toString(Charsets.UTF_8)
            }.getOrNull()

        if (
            decoded != null &&
            (
                decoded.contains(
                    "vless://",
                    ignoreCase = true
                ) ||
                decoded.contains(
                    "vmess://",
                    ignoreCase = true
                ) ||
                decoded.contains(
                    "trojan://",
                    ignoreCase = true
                )
            )
        ) {
            return decoded
        }

        return normalized
    }

    /**
     * کانفیگ‌های ساب را با کانفیگ‌های قبلی مقایسه می‌کند.
     *
     * کانفیگ‌هایی که source=sub دارند حذف می‌شوند
     * و نسخه جدید از sub.txt جایگزین می‌شود.
     *
     * کانفیگ‌های دستی کاربر دست‌نخورده باقی می‌مانند.
     */
    @Synchronized
    private fun updateSubscriptionProfiles(
        text: String
    ): ProfileLibrary {

        val candidates =
            ProfileUriParser
                .extractUris(text)
                .distinct()

        if (candidates.isEmpty()) {
            return snapshot()
        }

        val current =
            snapshot()
                .customProfiles
                .toMutableList()

        /*
         * فقط کانفیگ‌هایی که از Subscription قبلی آمده‌اند
         * حذف می‌شوند.
         *
         * کانفیگ دستی کاربر باقی می‌ماند.
         */
        current.removeAll { profile ->
            isSubscriptionProfile(profile)
        }

        val newProfiles =
            mutableListOf<ProxyProfile>()

        candidates.forEach { rawUri ->

            runCatching {

                val id =
                    subscriptionProfileId(rawUri)

                ProfileUriParser.parse(
                    rawUri,
                    id = id
                )

            }.onSuccess { profile ->

                /*
                 * کانفیگ را با شناسه مخصوص ساب ذخیره می‌کنیم.
                 */
                newProfiles += profile

            }
        }

        if (newProfiles.isNotEmpty()) {

            /*
             * کانفیگ‌های جدید ساب در ابتدای لیست قرار می‌گیرند.
             */
            current.addAll(
                0,
                newProfiles
            )

            writeProfiles(
                current.map { profile ->
                    profile.copy(
                        rawUri = profile.rawUri
                    )
                },
                subscriptionIds =
                    newProfiles
                        .map { it.id }
                        .toSet()
            )
        }

        /*
         * اگر کانفیگ انتخاب‌شده قبلی از ساب حذف شده باشد،
         * انتخاب را روی اولین کانفیگ معتبر می‌بریم.
         */
        val library =
            snapshot()

        val selectedStillExists =
            library.allProfiles.any {
                it.id == library.selectedId
            }

        if (!selectedStillExists) {

            val first =
                library.customProfiles.firstOrNull()

            if (first != null) {
                prefs.edit()
                    .putString(
                        KEY_SELECTED,
                        first.id
                    )
                    .apply()
            }
        }

        return snapshot()
    }

    /**
     * تشخیص اینکه پروفایل از Subscription آمده یا نه.
     *
     * برای سازگاری با فایل‌های قبلی،
     * علاوه بر ID، فیلد source هم بررسی می‌شود.
     */
    private fun isSubscriptionProfile(
        profile: ProxyProfile
    ): Boolean {

        if (
            profile.id.startsWith(
                SUBSCRIPTION_ID_PREFIX
            )
        ) {
            return true
        }

        return false
    }

    /**
     * برای هر URI یک ID ثابت می‌سازیم.
     *
     * بنابراین اگر کانفیگ در sub.txt جابه‌جا شود،
     * ID آن عوض نمی‌شود.
     */
    private fun subscriptionProfileId(
        uri: String
    ): String {

        val digest =
            MessageDigest.getInstance(
                "SHA-256"
            ).digest(
                uri.trim()
                    .toByteArray(Charsets.UTF_8)
            )

        val hash =
            digest.joinToString("") {
                "%02x".format(it)
            }

        return "$SUBSCRIPTION_ID_PREFIX${hash.take(32)}"
    }

    @Synchronized
    fun select(id: String): ProfileLibrary {

        val current =
            snapshot()

        require(
            current.allProfiles.any {
                it.id == id
            }
        ) {
            "Profile is no longer available"
        }

        prefs.edit()
            .putString(
                KEY_SELECTED,
                id
            )
            .apply()

        return current.copy(
            selectedId = id
        )
    }

    @Synchronized
    fun importText(
        text: String,
        nameOverride: String? = null
    ): ProfileImportResult {

        val candidates =
            ProfileUriParser
                .extractUris(text)
                .ifEmpty {

                    if (
                        text.trim().startsWith(
                            "vless://",
                            true
                        ) ||
                        text.trim().startsWith(
                            "trojan://",
                            true
                        ) ||
                        text.trim().startsWith(
                            "vmess://",
                            true
                        )
                    ) {
                        listOf(
                            text.trim()
                        )
                    } else {
                        emptyList()
                    }
                }

        if (candidates.isEmpty()) {
            return ProfileImportResult(
                snapshot(),
                0,
                listOf(
                    "No VLESS, Trojan or VMess URI found"
                )
            )
        }

        val current =
            snapshot()
                .customProfiles
                .toMutableList()

        val errors =
            mutableListOf<String>()

        var imported = 0

        candidates.forEachIndexed { index, raw ->

            runCatching {

                ProfileUriParser.parse(
                    raw,
                    nameOverride =
                        nameOverride.takeIf {
                            candidates.size == 1
                        }
                )

            }.onSuccess { profile ->

                current.removeAll { existing ->
                    !existing.isBuiltIn &&
                        ProfileUriParser.canonicalUri(
                            existing
                        ) ==
                        ProfileUriParser.canonicalUri(
                            profile
                        )
                }

                current.add(
                    0,
                    profile
                )

                imported++

            }.onFailure { error ->

                errors +=
                    "Item ${index + 1}: ${
                        error.message
                            ?: "invalid configuration"
                    }"
            }
        }

        if (imported > 0) {
            writeProfiles(current)
        }

        return ProfileImportResult(
            snapshot(),
            imported,
            errors
        )
    }

    @Synchronized
    fun importProfiles(
        profiles: List<ProxyProfile>
    ): ProfileImportResult {

        if (profiles.isEmpty()) {
            return ProfileImportResult(
                snapshot(),
                0,
                emptyList()
            )
        }

        val current =
            snapshot()
                .customProfiles
                .toMutableList()

        var imported = 0

        profiles.asReversed().forEach { profile ->

            if (profile.isBuiltIn) {
                return@forEach
            }

            val canonical =
                ProfileUriParser.canonicalUri(
                    profile
                )

            current.removeAll { existing ->
                existing.id == profile.id ||
                    ProfileUriParser.canonicalUri(
                        existing
                    ) == canonical
            }

            current.add(
                0,
                profile
            )

            imported++
        }

        if (imported > 0) {
            writeProfiles(current)
        }

        return ProfileImportResult(
            snapshot(),
            imported,
            emptyList()
        )
    }

    @Synchronized
    fun update(
        id: String,
        rawUri: String,
        name: String
    ): ProfileLibrary {

        require(
            !ProxyProfile.isProtectedBuiltIn(id)
        ) {
            "Built-in profile is read-only"
        }

        /*
         * Subscription configs را می‌توان از داخل برنامه ویرایش کرد،
         * ولی در Refresh بعدی نسخه GitHub دوباره جایگزین می‌شود.
         */
        val current =
            snapshot()
                .customProfiles
                .toMutableList()

        val index =
            current.indexOfFirst {
                it.id == id
            }

        require(index >= 0) {
            "Profile is no longer available"
        }

        current[index] =
            ProfileUriParser.parse(
                rawUri,
                id = id,
                nameOverride = name
            )

        writeProfiles(current)

        return snapshot()
    }

    @Synchronized
    fun updateCountry(
        id: String,
        country: CountryMetadata
    ): ProfileLibrary {

        require(
            !ProxyProfile.isProtectedBuiltIn(id)
        ) {
            "Built-in profile is read-only"
        }

        if (!country.isKnown) {
            return snapshot()
        }

        val current =
            snapshot()
                .customProfiles
                .toMutableList()

        val index =
            current.indexOfFirst {
                it.id == id
            }

        require(index >= 0) {
            "Profile is no longer available"
        }

        if (
            current[index].country ==
            country
        ) {
            return snapshot()
        }

        current[index] =
            current[index].copy(
                country = country
            )

        writeProfiles(current)

        return snapshot()
    }

    @Synchronized
    fun delete(
        id: String
    ): ProfileLibrary {
        return deleteMany(
            setOf(id)
        )
    }

    @Synchronized
    fun deleteMany(
        ids: Set<String>
    ): ProfileLibrary {

        require(
            ProxyProfile.BUILT_IN_ID !in ids &&
                ProxyProfile.BUILT_IN_2_ID !in ids
        ) {
            "Built-in profile is read-only"
        }

        if (ids.isEmpty()) {
            return snapshot()
        }

        val before =
            snapshot()

        val remaining =
            before.customProfiles
                .filterNot {
                    it.id in ids
                }

        writeProfiles(remaining)

        if (
            before.selectedId in ids
        ) {
            prefs.edit()
                .putString(
                    KEY_SELECTED,
                    ProxyProfile.BUILT_IN_ID
                )
                .apply()
        }

        return snapshot()
    }

    fun selectedProfile(): ProxyProfile =
        snapshot().selectedProfile

    @Synchronized
    fun markActive(
        id: String,
        endpoint: ProfileEndpoint
    ) {

        prefs.edit()
            .putString(
                KEY_ACTIVE,
                id
            )
            .putString(
                KEY_ACTIVE_HOST,
                endpoint.host
            )
            .putInt(
                KEY_ACTIVE_PORT,
                endpoint.port
            )
            .apply()
    }

    @Synchronized
    fun clearActive() {

        prefs.edit()
            .remove(KEY_ACTIVE)
            .remove(KEY_ACTIVE_HOST)
            .remove(KEY_ACTIVE_PORT)
            .apply()
    }

    fun activeProfile(): ProxyProfile? {

        val id =
            prefs.getString(
                KEY_ACTIVE,
                null
            ) ?: return null

        return snapshot()
            .allProfiles
            .firstOrNull {
                it.id == id
            }
    }

    fun activeEndpoint(): ProfileEndpoint? {

        val host =
            prefs.getString(
                KEY_ACTIVE_HOST,
                null
            )
                ?.trim()
                .orEmpty()

        val port =
            prefs.getInt(
                KEY_ACTIVE_PORT,
                0
            )

        return if (
            host.isNotBlank() &&
            port in 1..65_535
        ) {
            ProfileEndpoint(
                host,
                port
            )
        } else {
            null
        }
    }

    private fun readProfiles(): List<ProxyProfile> =
        runCatching {

            val array =
                JSONArray(
                    prefs.getString(
                        KEY_PROFILES,
                        "[]"
                    ) ?: "[]"
                )

            buildList {

                repeat(array.length()) { index ->

                    val item =
                        array.optJSONObject(
                            index
                        ) ?: return@repeat

                    val id =
                        item.optString("id")

                    val uri =
                        item.optString("uri")

                    val name =
                        item.optString("name")

                    val storedCountry =
                        CountryMetadata.resolve(
                            item.optString(
                                "countryCode"
                            ),
                            item.optString(
                                "countryName"
                            ),
                        )

                    runCatching {

                        ProfileUriParser.parse(
                            uri,
                            id = id,
                            nameOverride = name
                        ).let { profile ->

                            if (
                                storedCountry.isKnown
                            ) {
                                profile.copy(
                                    country =
                                        storedCountry
                                )
                            } else {
                                profile
                            }
                        }

                    }.getOrNull()
                        ?.let(::add)
                }
            }

        }.getOrDefault(
            emptyList()
        )

    /**
     * ذخیره پروفایل‌ها
     *
     * subscriptionIds فقط برای مستند بودن
     * ارسال می‌شود؛ شناسه خود پروفایل مشخص می‌کند
     * که از ساب آمده است.
     */
    private fun writeProfiles(
        profiles: List<ProxyProfile>,
        subscriptionIds: Set<String> = emptySet(),
    ) {

        val array =
            JSONArray()

        profiles.forEach { profile ->

            val persistedUri =
                profile.rawUri
                    .takeIf {
                        ProfileUriParser
                            .extractUris(it)
                            .isNotEmpty()
                    }
                    ?: ProfileUriParser
                        .canonicalUri(profile)

            val item =
                JSONObject()
                    .put(
                        "id",
                        profile.id
                    )
                    .put(
                        "name",
                        profile.name
                    )
                    .put(
                        "uri",
                        persistedUri
                    )

            /*
             * برای پروفایل‌های ساب یک علامت داخلی
             * ذخیره می‌کنیم.
             *
             * این مورد در UI نمایش داده نمی‌شود.
             */
            if (
                profile.id in subscriptionIds ||
                isSubscriptionProfile(profile)
            ) {
                item.put(
                    "source",
                    "subscription"
                )
            }

            if (
                profile.country.isKnown
            ) {
                item.put(
                    "countryCode",
                    profile.country.countryCode
                )

                item.put(
                    "countryName",
                    profile.country.countryName
                )
            }

            array.put(item)
        }

        prefs.edit()
            .putString(
                KEY_PROFILES,
                array.toString()
            )
            .apply()
    }

    private fun migrateLegacyOnce() {

        if (
            prefs.getBoolean(
                KEY_MIGRATED,
                false
            )
        ) {
            return
        }

        val legacy =
            appContext.getSharedPreferences(
                LEGACY_PREFS,
                Context.MODE_PRIVATE
            )

        val selectedLegacy =
            legacy.getLong(
                "selected",
                -1L
            )

        val migrated =
            mutableListOf<ProxyProfile>()

        var migratedSelection:
            String? = null

        runCatching {

            val old =
                JSONArray(
                    legacy.getString(
                        "profiles",
                        "[]"
                    ) ?: "[]"
                )

            repeat(old.length()) { index ->

                val item =
                    old.optJSONObject(
                        index
                    ) ?: return@repeat

                val oldId =
                    item.optLong(
                        "id",
                        -1L
                    )

                val oldName =
                    item.optString(
                        "name"
                    )

                val firstUri =
                    ProfileUriParser
                        .extractUris(
                            item.optString(
                                "content"
                            )
                        )
                        .firstOrNull()
                        ?: return@repeat

                val newId =
                    "legacy:$oldId"

                runCatching {

                    ProfileUriParser.parse(
                        firstUri,
                        id = newId,
                        nameOverride = oldName
                    )

                }.onSuccess {

                    migrated += it

                    if (
                        oldId ==
                        selectedLegacy
                    ) {
                        migratedSelection =
                            newId
                    }
                }
            }
        }

        if (
            migrated.isNotEmpty() &&
            readProfiles().isEmpty()
        ) {
            writeProfiles(migrated)
        }

        prefs.edit()
            .putBoolean(
                KEY_MIGRATED,
                true
            )
            .apply {

                migratedSelection?.let {
                    putString(
                        KEY_SELECTED,
                        it
                    )
                }

            }
            .apply()
    }

    companion object {

        private const val PREFS =
            "uac_proxy_profiles_v2"

        private const val KEY_PROFILES =
            "profiles"

        private const val KEY_SELECTED =
            "selected"

        private const val KEY_ACTIVE =
            "active"

        private const val KEY_ACTIVE_HOST =
            "active_host"

        private const val KEY_ACTIVE_PORT =
            "active_port"

        private const val KEY_MIGRATED =
            "legacy_migrated_v1"

        private const val KEY_LAST_SUB_REFRESH =
            "last_subscription_refresh"

        private const val LEGACY_PREFS =
            "uac_local_configs"

        private const val SUBSCRIPTION_ID_PREFIX =
            "subscription:"
    }
}
