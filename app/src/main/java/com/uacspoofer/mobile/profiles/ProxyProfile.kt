package com.uacspoofer.mobile.profiles

import com.uacspoofer.mobile.settings.AdvancedSettingsData

enum class ProxyProtocol(val wireName: String) {
    TROJAN("trojan"),
    VLESS("vless"),
    VMESS("vmess"),
}

data class ProxyProfile(
    val id: String,
    val name: String,
    val protocol: ProxyProtocol,
    val credential: String,
    val serverHost: String,
    val serverPort: Int,
    val network: String,
    val security: String,
    val sni: String,
    val host: String,
    val path: String,
    val alpn: String,
    val fingerprint: String,
    val allowInsecure: Boolean = false,
    val flow: String = "",
    val encryption: String = "none",
    val alterId: Int = 0,
    val serviceName: String = "",
    val authority: String = "",
    val xhttpMode: String = "",
    val xhttpExtra: String = "",
    val packetEncoding: String = "",
    val headerType: String = "",
    val country: CountryMetadata = CountryMetadata.UNKNOWN,
    val rawUri: String = "",
    val isBuiltIn: Boolean = false,

    // Reality
    val realityPublicKey: String = "",
    val realityShortId: String = "",
) {

    fun usesAdvancedSettingsIdentity(): Boolean =
        isBuiltIn && id == BUILT_IN_ID

    fun runtimeIdentity(settings: AdvancedSettingsData): RuntimeProxyIdentity =
        if (usesAdvancedSettingsIdentity()) {
            RuntimeProxyIdentity(
                protocol = ProxyProtocol.TROJAN,
                credential = settings.trojanPassword,
                network = settings.transportNetwork,
                security = settings.transportSecurity,
                sni = settings.tlsSni,
                host = settings.wsHost,
                path = settings.wsPath,
                alpn = TlsAlpnResolver.canonicalString(
                    settings.tlsAlpn,
                    settings.transportNetwork
                ),
                fingerprint = settings.tlsFingerprint,
                allowInsecure = false,
                flow = "",
                encryption = "none",
                alterId = 0,
                serviceName = "",
                authority = "",
                xhttpMode = "",
                xhttpExtra = "",
                packetEncoding = "",
                headerType = "",
                realityPublicKey = "",
                realityShortId = "",
            )
        } else {
            RuntimeProxyIdentity(
                protocol = protocol,
                credential = credential,
                network = network,
                security = security,
                sni = sni,
                host = host,
                path = path,
                alpn = TlsAlpnResolver.canonicalString(alpn, network),
                fingerprint = fingerprint,
                allowInsecure = allowInsecure,
                flow = flow,
                encryption = encryption,
                alterId = alterId,
                serviceName = serviceName,
                authority = authority,
                xhttpMode = xhttpMode,
                xhttpExtra = xhttpExtra,
                packetEncoding = packetEncoding,
                headerType = headerType,
                realityPublicKey = realityPublicKey,
                realityShortId = realityShortId,
            )
        }

    companion object {

        const val BUILT_IN_ID = "builtin:mci"
        const val BUILT_IN_2_ID = "builtin:mci2"

        val UAC_SNI_BUILT_IN = ProxyProfile(
            id = BUILT_IN_ID,
            name = "UAC SNI built-in",
            protocol = ProxyProtocol.TROJAN,
            credential = "humanity",
            serverHost = "www.ignitelimit.com",
            serverPort = 443,
            network = "ws",
            security = "tls",
            sni = "www.ignitelimit.com",
            host = "www.ignitelimit.com",
            path = "/assignment",
            alpn = "http/1.1",
            fingerprint = "chrome",
            country = CountryMetadata.resolve("FR", "France"),
            isBuiltIn = true,
        )

        val UAC_SNI_BUILT_IN_2 = ProxyProfile(
            id = BUILT_IN_2_ID,
            name = "UAC SNI built-in 2",
            protocol = ProxyProtocol.TROJAN,
            credential = "humanity",
            serverHost = "127.0.0.1",
            serverPort = 40443,
            network = "ws",
            security = "tls",
            sni = "api-ir.behroozuac.dpdns.org",
            host = "api-ir.behroozuac.dpdns.org",
            path = "/assignment",
            alpn = "http/1.1",
            fingerprint = "chrome",
            country = CountryMetadata.resolve("NL", "Netherlands"),
            rawUri =
                "trojan://humanity@127.0.0.1:40443?type=ws&security=tls" +
                "&sni=api-ir.behroozuac.dpdns.org" +
                "&host=api-ir.behroozuac.dpdns.org" +
                "&path=%2Fassignment&alpn=http%2F1.1&fp=chrome#humanity-user",
            isBuiltIn = true,
        )

        // 3
        val FAST_TR_X2 = ProxyProfile(
            id = "builtin:fast-tr-x2",
            name = "⚡️Fast 🇹🇷 Turkey X2",
            protocol = ProxyProtocol.VLESS,
            credential = "f71add53-5ff1-4946-9528-e724e91a7fb6",
            serverHost = "fastter.panelsaaz.ir",
            serverPort = 2096,
            network = "ws",
            security = "tls",
            sni = "fastter.panelsaaz.ir",
            host = "fastter.panelsaaz.ir",
            path = "/upl",
            alpn = "",
            fingerprint = "",
            flow = "xtls-rprx-vision",
            encryption = "mlkem768x25519plus.native.0rtt.COIMziHJqqejfGNE3gd7EhVJyAt6BOcVeLWw-UqPDGcqHcBvZup4_JRTQDFjk8ILEBo0OWE3CqQwfVoUYqdNcSoNiKqVQXMjOXsfDMd8IOXEQ9UdC6B8G7RNyvk_DRJNLJEL19KPWbiZKQuDzjkHCvihiolgZ5SFRLt0yHFibdR-3oFcEzJtWJZUdESiTJaIxRIkPbKr2chLxhGNRBu35SlOx4x7eaHCqYyv5aHKUQYm0aOYR8lVBZkzbqZYupUmDlBNkAhTe1XDTXs_pcgYngJQt_SCrzJkcKxI40sgMOVOwAwDDFmGInBNpTBPqdh3CfSBhOJ15fQ1WVxnc7mz0TaVKiYXNSWyMyFCf8Uln_GbRjlA2cmvgzYUEZoUroQrSJwYCjS414udf5bHB9RdDAuRGIcw84xACUJLkPxKduS4Jka-AvHOCvaoE-xoHii4LNxieGmR5sZnG5xa3qYUtOgQ2IZaONQc5ESGDRKV6-NljLTDMKZLJ4E9XHKfytB0XUdTf2oT_MkwykPBUVJ89bQgPrtf6gfMx4gM4BJbeipZfDKoUWFz_RcXL3IibKk3MXWrjbdCkzGHPbt2WGOgpKEcrkujFmI6ahll5GVMLePAvGuFPjKH8LlFK0O1vse64AKV3XFdIeqwq9Clv8p83sCwT7wr8dFA3wMqC9SY9JI8mCZRMWoVulIec5QuUzeTnitIOOC8u_cM_rY7fHeoYWCi_jxNhIu8qWSRf3LJPnArhDOKeNm0IqELHwCgcBwH_SmgcoZCKvOvK2LMlKq6pVObilx82RIMIWh-zAFRe2q4JcqgHPu0bulNF-bLa1impzkNk2suhFSII_K8QWm9f7OHieFRx3CK-hEKaqCCU1p-entVASs1iFidgDxyO_ejEvSSYBgruAcM1bQDyQFS_ty_3DgP1sGAGOuyhYsryQF2KMxm_3QB9zGWKXtbcNkDTfuS4TSinUc0xKMsDruI14BftZFW08wtxwiBQFePa0MRmNqKauIe55SRQXU96GhD4rWkcNKgK8k7ozyL8emxxoQtR1mXbVCPNgpKBWK4XqMY7InDgiSaZnCFhkWpRfV6ybtZnFYzElNtHSOcpEF36utePTElX3q08GIZbSK7CKto3pIE28nMtxuopGmoHVdkFQDQmvwRngyT4GSfUbMz5glyStVsqXu7BwodHYTNoQNSissdc8twz6d4Y_PJxlmbnURMQMut6hK1xQUfDSIKVeWCrAkXe9c6imkxMat40WvJmagXsjK9mKpIVaSNxbrM5aCs-hNxrKCqVskZn1uE2slWZUvAkOAKlsRcZDauFsXIqoQrVZWrlSi2O8lgvMKs8AFoggcmAJpSGdQoZhpsMVuPEHKkp9efa8YK0BpS9hqDxPpy7WBIbraokHyLH6XPNQG2kDwIqtCjMsALOUUWMgdZmkVgtytTQSwhlls0lagszuCTOEE5xqVfYuqhtLDNqTg1P0pdGfW3pYV6P2fA1YaDI_gWbjub-sUjkhlkJAts0BSa9amnkWgNWFB5lSIAdvVxn0oAZcbg6iBU42iJ5nJ1tYodaw0QwipdiH0eYRQ&security=tls&type=ws&headerType=none&flow=xtls-rprx-vision&path=%2Fupl&host=fastter.panelsaaz.ir&sni=fastter.panelsaaz.ir",
            country = CountryMetadata.resolve("TR", "Turkey"),
            isBuiltIn = true,
        )

        // 4
        val FAST_DE_X2 = ProxyProfile(
            id = "builtin:fast-de-x2",
            name = "⚡️Fast 🇩🇪 Germany X2",
            protocol = ProxyProtocol.VLESS,
            credential = "f71add53-5ff1-4946-9528-e724e91a7fb6",
            serverHost = "fastus.panelsaaz.ir",
            serverPort = 2083,
            network = "ws",
            security = "tls",
            sni = "fastus.panelsaaz.ir",
            host = "fastus.panelsaaz.ir",
            path = "/arv",
            alpn = "",
            fingerprint = "",
            flow = "xtls-rprx-vision",
            encryption = "mlkem768x25519plus.native.0rtt.MFC6Mplb2XwpSnBGdHaNq3EUFm0cPMz-gOF9KYPVvW4",
            country = CountryMetadata.resolve("DE", "Germany"),
            isBuiltIn = true,
        )

        // 5
        val TURKEY_TCP = ProxyProfile(
            id = "builtin:turkey-tcp",
            name = "🇹🇷 Turkey TCP",
            protocol = ProxyProtocol.VLESS,
            credential = "f71add53-5ff1-4946-9528-e724e91a7fb6",
            serverHost = "trnewpnlszhp.mamadhpbot.ir",
            serverPort = 8080,
            network = "tcp",
            security = "none",
            sni = "",
            host = "www.goo.gl",
            path = "/",
            alpn = "",
            fingerprint = "",
            encryption = "none",
            headerType = "http",
            country = CountryMetadata.resolve("TR", "Turkey"),
            isBuiltIn = true,
        )

        // 6
        val BRAZIL_REALITY = ProxyProfile(
            id = "builtin:brazil-reality",
            name = "🇧🇷 Brazil",
            protocol = ProxyProtocol.VLESS,
            credential = "f71add53-5ff1-4946-9528-e724e91a7fb6",
            serverHost = "brazilenewab.mamadhpbot.ir",
            serverPort = 50118,
            network = "tcp",
            security = "reality",
            sni = "play.google.com",
            host = "play.google.com",
            path = "/",
            alpn = "",
            fingerprint = "firefox",
            encryption = "none",
            headerType = "http",
            realityPublicKey = "mH5-bF1qU4omWmdGD2IBSV4NA-gIPc96jKksAxKelUw",
            realityShortId = "d4b375672913f275",
            country = CountryMetadata.resolve("BR", "Brazil"),
            isBuiltIn = true,
        )

        // 7
        val TURKEY_REALITY = ProxyProfile(
            id = "builtin:turkey-reality",
            name = "🇹🇷 Turkey Reality",
            protocol = ProxyProtocol.VLESS,
            credential = "f71add53-5ff1-4946-9528-e724e91a7fb6",
            serverHost = "turkeypnlsazabc.mamadhpbot.ir",
            serverPort = 50118,
            network = "tcp",
            security = "reality",
            sni = "play.google.com",
            host = "play.google.com",
            path = "/",
            alpn = "",
            fingerprint = "firefox",
            encryption = "none",
            headerType = "http",
            realityPublicKey = "mH5-bF1qU4omWmdGD2IBSV4NA-gIPc96jKksAxKelUw",
            realityShortId = "d4b375672913f275",
            country = CountryMetadata.resolve("TR", "Turkey"),
            isBuiltIn = true,
        )

        // 8
        val FRANCE_REALITY = ProxyProfile(
            id = "builtin:france-reality",
            name = "🇫🇷 France Reality",
            protocol = ProxyProtocol.VLESS,
            credential = "f71add53-5ff1-4946-9528-e724e91a7fb6",
            serverHost = "farancendpnlsaznew.mamadhpbot.ir",
            serverPort = 50118,
            network = "tcp",
            security = "reality",
            sni = "play.google.com",
            host = "play.google.com",
            path = "/",
            alpn = "",
            fingerprint = "firefox",
            encryption = "none",
            headerType = "http",
            realityPublicKey = "mH5-bF1qU4omWmdGD2IBSV4NA-gIPc96jKksAxKelUw",
            realityShortId = "d4b375672913f275",
            country = CountryMetadata.resolve("FR", "France"),
            isBuiltIn = true,
        )

        // 9
        val USA_REALITY = ProxyProfile(
            id = "builtin:usa-reality",
            name = "🇺🇸 USA MCI",
            protocol = ProxyProtocol.VLESS,
            credential = "f71add53-5ff1-4946-9528-e724e91a7fb6",
            serverHost = "usnodenew.mamadhpbot.ir",
            serverPort = 50118,
            network = "tcp",
            security = "reality",
            sni = "play.google.com",
            host = "play.google.com",
            path = "/",
            alpn = "",
            fingerprint = "chrome",
            encryption = "none",
            headerType = "http",
            realityPublicKey = "mH5-bF1qU4omWmdGD2IBSV4NA-gIPc96jKksAxKelUw",
            realityShortId = "d4b375672913f275",
            country = CountryMetadata.resolve("US", "USA"),
            isBuiltIn = true,
        )

        // 10
        val GERMANY_REALITY = ProxyProfile(
            id = "builtin:germany-reality",
            name = "🇩🇪 Germany Reality",
            protocol = ProxyProtocol.VLESS,
            credential = "f71add53-5ff1-4946-9528-e724e91a7fb6",
            serverHost = "germanpnlsaz.mamadhpbot.ir",
            serverPort = 50118,
            network = "tcp",
            security = "reality",
            sni = "play.google.com",
            host = "germanpnlsaz.mamadhpbot.ir",
            path = "/",
            alpn = "",
            fingerprint = "chrome",
            encryption = "none",
            headerType = "http",
            realityPublicKey = "mH5-bF1qU4omWmdGD2IBSV4NA-gIPc96jKksAxKelUw",
            realityShortId = "d4b375672913f275",
            country = CountryMetadata.resolve("DE", "Germany"),
            isBuiltIn = true,
        )

        val BUILT_IN_PROFILES = listOf(
            UAC_SNI_BUILT_IN,
            UAC_SNI_BUILT_IN_2,
            FAST_TR_X2,
            FAST_DE_X2,
            TURKEY_TCP,
            BRAZIL_REALITY,
            TURKEY_REALITY,
            FRANCE_REALITY,
            USA_REALITY,
            GERMANY_REALITY,
        )

        fun isProtectedBuiltIn(id: String): Boolean =
            BUILT_IN_PROFILES.any { it.id == id }
    }
}

data class RuntimeProxyIdentity(
    val protocol: ProxyProtocol,
    val credential: String,
    val network: String,
    val security: String,
    val sni: String,
    val host: String,
    val path: String,
    val alpn: String,
    val fingerprint: String,
    val allowInsecure: Boolean,
    val flow: String,
    val encryption: String,
    val alterId: Int,
    val serviceName: String,
    val authority: String,
    val xhttpMode: String = "",
    val xhttpExtra: String = "",
    val packetEncoding: String = "",
    val headerType: String = "",
    val realityPublicKey: String = "",
    val realityShortId: String = "",
) {
    val usesTls: Boolean
        get() = security == "tls" || security == "reality"
}

data class ProfileLibrary(
    val customProfiles: List<ProxyProfile>,
    val selectedId: String,
) {
    val allProfiles: List<ProxyProfile>
        get() = ProxyProfile.BUILT_IN_PROFILES + customProfiles

    val selectedProfile: ProxyProfile
        get() = allProfiles.firstOrNull { it.id == selectedId }
            ?: ProxyProfile.UAC_SNI_BUILT_IN
}
