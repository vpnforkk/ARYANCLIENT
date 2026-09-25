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
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

data class ProfileImportResult(
    val library: ProfileLibrary,
    val importedCount: Int,
    val errors: List<String>,
)

class ProfileStore(context: Context) {

    private val appContext = context.applicationContext

    private val prefs =
        appContext.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )

    private val refreshExecutor =
        Executors.newSingleThreadExecutor()

    private val mainHandler =
        Handler(Looper.getMainLooper())

    /*
     * ============================================================
     * آدرس Subscription آریان کلاینت
     * ============================================================
     */
    private val subscriptionUrl =
        "https://narmafzar090.github.io/my-sub/sub.txt"

    /*
     * ============================================================
     * هر چند وقت یک بار Subscription بررسی شود
     *
     * 60 ثانیه = 1 دقیقه
     * ============================================================
     */
    private val refreshIntervalMs =
        60_000L

    /*
     * جلوگیری از اجرای همزمان چند Refresh
     */
    @Volatile
    private var refreshRunning = false

    private var scheduledRefresh: ScheduledFuture<*>? = null

    init {
        startAutomaticSubscriptionRefresh()
    }

    /**
     * شروع بررسی خودکار Subscription
     *
     * بار اول بعد از چند ثانیه اجرا می‌شود
     * و سپس هر 1 دقیقه تکرار می‌شود.
     */
    private fun startAutomaticSubscriptionRefresh() {

        if (scheduledRefresh != null) {
            return
        }

        scheduledRefresh =
            refreshExecutor.scheduleWithFixedDelay(
                {
                    runCatching {
                        refreshRemoteSubscription(
                            force = false,
                            onResult = null
                        )
                    }
                },
                3_000L,
                refreshIntervalMs,
                TimeUnit.MILLISECONDS
            )
    }

    /**
     * توقف Refresh خودکار
     *
     * در صورت نیاز از خارج کلاس قابل استفاده است.
     */
    fun stopAutomaticSubscriptionRefresh() {

        scheduledRefresh?.cancel(false)
        scheduledRefresh = null
    }

    /**
     * بستن کامل Executor
     */
    fun shutdown() {

        stopAutomaticSubscriptionRefresh()

        refreshExecutor.shutdownNow()
    }

    /**
     * دریافت وضعیت فعلی Library
     */
    @Synchronized
    fun snapshot(): ProfileLibrary {

        migrateLegacyOnce()

        val profiles =
            readProfiles()

        val requested =
            prefs.getString(
                KEY_SELECTED,
                ProxyProfile.BUILT_IN_ID
            )
                ?: ProxyProfile.BUILT_IN_ID

        val selected =
            requested.takeIf { id ->
                ProxyProfile.isProtectedBuiltIn(id) ||
                    profiles.any { it.id == id }
            }
                ?: ProxyProfile.BUILT_IN_ID

        if (selected != requested) {

            prefs.edit()
                .putString(
                    KEY_SELECTED,
                    selected
                )
                .apply()
        }

        return ProfileLibrary(
            profiles,
            selected
        )
    }

    /**
     * دریافت Subscription
     *
     * UI قفل نمی‌شود.
     */
    fun refreshRemoteSubscription(
        force: Boolean = false,
        onResult: ((ProfileLibrary) -> Unit)? = null,
    ) {

        if (refreshRunning) {
            return
        }

        val now =
            System.currentTimeMillis()

        val lastRefresh =
            prefs.getLong(
                KEY_LAST_SUB_REFRESH,
                0L
            )

        if (
            !force &&
            now - lastRefresh < refreshIntervalMs
        ) {

            onResult?.let { callback ->

                mainHandler.post {
                    callback(snapshot())
                }
            }

            return
        }

        refreshRunning = true

        refreshExecutor.execute {

            try {

                val result =
                    runCatching {
                        downloadSubscription()
                    }

                result
                    .onSuccess { text ->

                        val updated =
                            synchronized(this) {
                                updateSubscriptionProfiles(
                                    text
                                )
                            }

                        prefs.edit()
                            .putLong(
                                KEY_LAST_SUB_REFRESH,
                                System.currentTimeMillis()
                            )
                            .apply()

                        mainHandler.post {

                            onResult?.invoke(
                                updated
                            )
                        }
                    }
                    .onFailure {

                        mainHandler.post {

                            onResult?.invoke(
                                snapshot()
                            )
                        }
                    }

            } finally {

                refreshRunning = false
            }
        }
    }

    /**
     * دانلود فایل Subscription
     */
    private fun downloadSubscription(): String {

        val connection =
            (
                URL(subscriptionUrl)
                    .openConnection()
                    as HttpURLConnection
            ).apply {

                requestMethod = "GET"

                connectTimeout =
                    10_000

                readTimeout =
                    15_000

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

            val responseCode =
                connection.responseCode

            require(
                responseCode in 200..299
            ) {
                "Subscription HTTP error: $responseCode"
            }

            val text =
                connection.inputStream
                    .bufferedReader(
                        Charsets.UTF_8
                    )
                    .use {
                        it.readText()
                    }
                    .trim()

            require(
                text.isNotBlank()
            ) {
                "Subscription is empty"
            }

            return decodeSubscriptionIfNeeded(
                text
            )

        } finally {

            connection.disconnect()
        }
    }

    /**
     * تشخیص Subscription معمولی یا Base64
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
                .replace(
                    "\\s".toRegex(),
                    ""
                )

        val decoded =
            runCatching {

                Base64.decode(
                    compact,
                    Base64.DEFAULT or
                        Base64.NO_WRAP
                )
                    .toString(
                        Charsets.UTF_8
                    )

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
     * جایگزینی کانفیگ‌های Subscription
     *
     * کانفیگ‌های دستی کاربر باقی می‌مانند.
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
         * حذف نسخه قبلی کانفیگ‌های Subscription
         */
        current.removeAll { profile ->

            isSubscriptionProfile(
                profile
            )
        }

        val newProfiles =
            mutableListOf<ProxyProfile>()

        candidates.forEach { rawUri ->

            runCatching {

                val id =
                    subscriptionProfileId(
                        rawUri
                    )

                ProfileUriParser.parse(
                    rawUri,
                    id = id
                )

            }.onSuccess { profile ->

                newProfiles += profile
            }
        }

        if (newProfiles.isEmpty()) {

            return snapshot()
        }

        /*
         * کانفیگ‌های جدید Subscription
         * در ابتدای لیست قرار می‌گیرند.
         */
        current.addAll(
            0,
            newProfiles
        )

        writeProfiles(
            profiles = current,
            subscriptionIds =
                newProfiles
                    .map {
                        it.id
                    }
                    .toSet()
        )

        /*
         * اگر کانفیگ انتخاب‌شده قبلی از Subscription
         * حذف شده باشد، اولین کانفیگ جدید انتخاب می‌شود.
         */
        val after =
            snapshot()

        val selectedExists =
            after.allProfiles.any {
                it.id == after.selectedId
            }

        if (!selectedExists) {

            val first =
                after.customProfiles
                    .firstOrNull()

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
     * تشخیص کانفیگ Subscription
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
     * ساخت ID ثابت برای هر URI
     *
     * اگر ترتیب کانفیگ‌ها عوض شود،
     * ID آن‌ها تغییر نمی‌کند.
     */
    private fun subscriptionProfileId(
        uri: String
    ): String {

        val digest =
            MessageDigest.getInstance(
                "SHA-256"
            ).digest(
                uri.trim()
                    .toByteArray(
                        Charsets.UTF_8
                    )
            )

        val hash =
            digest.joinToString("") {

                "%02x".format(it)
            }

        return "$SUBSCRIPTION_ID_PREFIX${hash.take(32)}"
    }

    /**
     * انتخاب کانفیگ
     */
    @Synchronized
    fun select(
        id: String
    ): ProfileLibrary {

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

    /**
     * وارد کردن کانفیگ دستی
     */
    @Synchronized
    fun importText(
        text: String,
        nameOverride: String? = null
    ): ProfileImportResult {

        val candidates =
            ProfileUriParser
                .extractUris(text)
                .ifEmpty {

                    val trimmed =
                        text.trim()

                    if (
                        trimmed.startsWith(
                            "vless://",
                            true
                        ) ||
                        trimmed.startsWith(
                            "trojan://",
                            true
                        ) ||
                        trimmed.startsWith(
                            "vmess://",
                            true
                        )
                    ) {

                        listOf(
                            trimmed
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

        var imported =
            0

        candidates.forEachIndexed {
                index,
                raw ->

            runCatching {

                ProfileUriParser.parse(
                    raw,
                    nameOverride =
                        nameOverride.takeIf {
                            candidates.size == 1
                        }
                )

            }.onSuccess { profile ->

                current.removeAll {
                    existing ->

                    !existing.isBuiltIn &&
                        ProfileUriParser
                            .canonicalUri(
                                existing
                            ) ==
                        ProfileUriParser
                            .canonicalUri(
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

            writeProfiles(
                current
            )
        }

        return ProfileImportResult(
            snapshot(),
            imported,
            errors
        )
    }

    /**
     * وارد کردن چند پروفایل
     */
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

        var imported =
            0

        profiles
            .asReversed()
            .forEach { profile ->

                if (profile.isBuiltIn) {
                    return@forEach
                }

                val canonical =
                    ProfileUriParser
                        .canonicalUri(
                            profile
                        )

                current.removeAll {
                    existing ->

                    existing.id == profile.id ||
                        ProfileUriParser
                            .canonicalUri(
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

            writeProfiles(
                current
            )
        }

        return ProfileImportResult(
            snapshot(),
            imported,
            emptyList()
        )
    }

    /**
     * ویرایش کانفیگ
     */
    @Synchronized
    fun update(
        id: String,
        rawUri: String,
        name: String
    ): ProfileLibrary {

        require(
            !ProxyProfile
                .isProtectedBuiltIn(id)
        ) {
            "Built-in profile is read-only"
        }

        val current =
            snapshot()
                .customProfiles
                .toMutableList()

        val index =
            current.indexOfFirst {
                it.id == id
            }

        require(
            index >= 0
        ) {
            "Profile is no longer available"
        }

        current[index] =
            ProfileUriParser.parse(
                rawUri,
                id = id,
                nameOverride = name
            )

        writeProfiles(
            current
        )

        return snapshot()
    }

    /**
     * ذخیره کشور کانفیگ
     */
    @Synchronized
    fun updateCountry(
        id: String,
        country: CountryMetadata
    ): ProfileLibrary {

        require(
            !ProxyProfile
                .isProtectedBuiltIn(id)
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

        require(
            index >= 0
        ) {
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

        writeProfiles(
            current
        )

        return snapshot()
    }

    /**
     * حذف یک کانفیگ
     */
    @Synchronized
    fun delete(
        id: String
    ): ProfileLibrary {

        return deleteMany(
            setOf(id)
        )
    }

    /**
     * حذف چند کانفیگ
     */
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

        writeProfiles(
            remaining
        )

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

    /**
     * کانفیگ انتخاب‌شده
     */
    fun selectedProfile():
        ProxyProfile =
        snapshot()
            .selectedProfile

    /**
     * ذخیره کانفیگ فعال
     */
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

    /**
     * پاک کردن کانفیگ فعال
     */
    @Synchronized
    fun clearActive() {

        prefs.edit()
            .remove(KEY_ACTIVE)
            .remove(KEY_ACTIVE_HOST)
            .remove(KEY_ACTIVE_PORT)
            .apply()
    }

    /**
     * کانفیگ فعال
     */
    fun activeProfile():
        ProxyProfile? {

        val id =
            prefs.getString(
                KEY_ACTIVE,
                null
            )
                ?: return null

        return snapshot()
            .allProfiles
            .firstOrNull {
                it.id == id
            }
    }

    /**
     * Endpoint فعال
     */
    fun activeEndpoint():
        ProfileEndpoint? {

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

    /**
     * خواندن پروفایل‌ها
     */
    private fun readProfiles():
        List<ProxyProfile> =

        runCatching {

            val array =
                JSONArray(
                    prefs.getString(
                        KEY_PROFILES,
                        "[]"
                    )
                        ?: "[]"
                )

            buildList {

                repeat(
                    array.length()
                ) { index ->

                    val item =
                        array.optJSONObject(
                            index
                        )
                            ?: return@repeat

                    val id =
                        item.optString(
                            "id"
                        )

                    val uri =
                        item.optString(
                            "uri"
                        )

                    val name =
                        item.optString(
                            "name"
                        )

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

                    }
                        .getOrNull()
                        ?.let(::add)
                }
            }

        }
            .getOrDefault(
                emptyList()
            )

    /**
     * ذخیره پروفایل‌ها
     */
    private fun writeProfiles(
        profiles: List<ProxyProfile>,
        subscriptionIds: Set<String> =
            emptySet(),
    ) {

        val array =
            JSONArray()

        profiles.forEach { profile ->

            val persistedUri =
                profile.rawUri
                    .takeIf {

                        ProfileUriParser
                            .extractUris(
                                it
                            )
                            .isNotEmpty()

                    }
                    ?: ProfileUriParser
                        .canonicalUri(
                            profile
                        )

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
             * علامت داخلی Subscription
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

    /**
     * مهاجرت نسخه قدیمی
     */
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
                    )
                        ?: "[]"
                )

            repeat(
                old.length()
            ) { index ->

                val item =
                    old.optJSONObject(
                        index
                    )
                        ?: return@repeat

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

            writeProfiles(
                migrated
            )
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
