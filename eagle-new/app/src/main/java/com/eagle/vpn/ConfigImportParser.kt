package com.eagle.vpn

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object ConfigImportParser {
    private val schemes = listOf(
        "vless://", "vmess://", "trojan://", "ss://",
        "socks://", "socks5://", "http://"
    )

    fun convert(raw: String): String {
        var input = raw.trim().removePrefix("\uFEFF").trim()
        require(input.isNotEmpty()) { "ورودی کانفیگ خالی است" }

        if (input.startsWith("{")) return input

        var lines = input.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }

        if (lines.none { line -> schemes.any { line.startsWith(it, true) } }) {
            val decoded = decodeBase64(input)
            if (decoded != null && schemes.any { decoded.contains(it, true) }) {
                lines = decoded.lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() && !it.startsWith("#") }
            }
        }

        val links = lines.filter { line ->
            schemes.any { line.startsWith(it, true) }
        }
        require(links.isNotEmpty()) {
            "فرمت ناشناخته است؛ لینک پروتکل یا JSON معتبر وارد کنید"
        }

        val outbounds = JSONArray()
        links.forEachIndexed { index, link ->
            outbounds.put(parseLink(link, "proxy-${index + 1}"))
        }
        outbounds.put(JSONObject().put("type", "direct").put("tag", "direct"))
        outbounds.put(JSONObject().put("type", "block").put("tag", "block"))

        val firstProxy = "proxy-1"
        val dns = JSONObject()
            .put("servers", JSONArray().put(
                JSONObject()
                    .put("tag", "remote-dns")
                    .put("address", "https://1.1.1.1/dns-query")
                    .put("detour", firstProxy)
            ))
            .put("final", "remote-dns")

        val tun = JSONObject()
            .put("type", "tun")
            .put("tag", "tun-in")
            .put("address", JSONArray()
                .put("172.19.0.1/30")
                .put("fdfe:dcba:9876::1/126"))
            .put("auto_route", true)
            .put("strict_route", true)

        val route = JSONObject()
            .put("auto_detect_interface", true)
            .put("final", firstProxy)

        return JSONObject()
            .put("log", JSONObject().put("level", "info"))
            .put("dns", dns)
            .put("inbounds", JSONArray().put(tun))
            .put("outbounds", outbounds)
            .put("route", route)
            .toString(2)
    }

    private fun parseLink(link: String, tag: String): JSONObject {
        return when {
            link.startsWith("vmess://", true) -> parseVmess(link, tag)
            link.startsWith("ss://", true) -> parseShadowsocks(link, tag)
            else -> parseUri(link, tag)
        }
    }

    private fun parseUri(link: String, tag: String): JSONObject {
        val uri = URI(link)
        val scheme = uri.scheme?.lowercase()
            ?: throw IllegalArgumentException("نوع لینک مشخص نیست")
        val host = uri.host
            ?: throw IllegalArgumentException("آدرس سرور در لینک وجود ندارد")
        val port = uri.port
        require(port in 1..65535) { "پورت سرور نامعتبر است" }

        val query = parseQuery(uri.rawQuery)
        val userInfo = uri.rawUserInfo.orEmpty()
        val user = userInfo.substringBefore(':').urlDecode()
        val password = userInfo.substringAfter(':', "").urlDecode()
        val name = uri.rawFragment?.urlDecode().orEmpty()

        val outbound = JSONObject()
            .put("tag", tag)
            .put("server", host)
            .put("server_port", port)

        when (scheme) {
            "vless" -> {
                require(user.isNotBlank()) { "شناسه UUID در لینک VLESS خالی است" }
                outbound.put("type", "vless").put("uuid", user)
                query["flow"]?.takeIf { it.isNotBlank() }?.let {
                    outbound.put("flow", it)
                }
                addTls(outbound, query, host, scheme)
                addTransport(outbound, query)
            }
            "trojan" -> {
                require(userInfo.isNotBlank()) { "رمز لینک Trojan وجود ندارد" }
                outbound.put("type", "trojan")
                    .put("password", if (password.isNotEmpty()) password else user)
                addTls(outbound, query, host, scheme)
                addTransport(outbound, query)
            }
            "socks", "socks5" -> {
                outbound.put("type", "socks")
                if (user.isNotBlank()) outbound.put("username", user)
                if (password.isNotBlank()) outbound.put("password", password)
            }
            "http" -> {
                outbound.put("type", "http")
                if (user.isNotBlank()) outbound.put("username", user)
                if (password.isNotBlank()) outbound.put("password", password)
            }
            else -> throw IllegalArgumentException("این پروتکل هنوز پشتیبانی نمی‌شود: $scheme")
        }

        if (name.isNotBlank()) outbound.put("tag", tag)
        return outbound
    }

    private fun parseVmess(link: String, tag: String): JSONObject {
        val encoded = link.substringAfter("://").substringBefore('#').trim()
        val text = decodeBase64(encoded)
            ?: throw IllegalArgumentException("دادهٔ لینک VMess قابل رمزگشایی نیست")
        val data = JSONObject(text)
        val host = data.optString("add").ifBlank { data.optString("host") }
        val port = data.optString("port").toIntOrNull()
            ?: throw IllegalArgumentException("پورت VMess نامعتبر است")
        val uuid = data.optString("id")
        require(host.isNotBlank() && uuid.isNotBlank()) {
            "آدرس سرور یا UUID در لینک VMess وجود ندارد"
        }
        require(port in 1..65535) { "پورت VMess خارج از محدوده است" }

        val outbound = JSONObject()
            .put("type", "vmess")
            .put("tag", tag)
            .put("server", host)
            .put("server_port", port)
            .put("uuid", uuid)
            .put("security", data.optString("scy").ifBlank { "auto" })

        val tlsEnabled = data.optString("tls").equals("tls", true)
        if (tlsEnabled) {
            val tls = JSONObject()
                .put("enabled", true)
                .put("server_name", data.optString("sni").ifBlank { host })
            val fp = data.optString("fp")
            if (fp.isNotBlank()) {
                tls.put("utls", JSONObject().put("enabled", true).put("fingerprint", fp))
            }
            outbound.put("tls", tls)
        }

        val network = data.optString("net").lowercase()
        when (network) {
            "ws" -> {
                val transport = JSONObject()
                    .put("type", "ws")
                    .put("path", data.optString("path", "/"))
                val wsHost = data.optString("host")
                if (wsHost.isNotBlank()) {
                    transport.put("headers", JSONObject().put("Host", wsHost))
                }
                outbound.put("transport", transport)
            }
            "grpc" -> outbound.put("transport", JSONObject()
                .put("type", "grpc")
                .put("service_name", data.optString("path")))
            "httpupgrade" -> outbound.put("transport", JSONObject()
                .put("type", "httpupgrade")
                .put("path", data.optString("path", "/")))
        }
        return outbound
    }

    private fun parseShadowsocks(link: String, tag: String): JSONObject {
        var body = link.substringAfter("://").substringBefore('#')
        body = body.substringBefore('?').trim()

        if (!body.contains('@')) {
            val decoded = decodeBase64(body)
            if (decoded != null) body = decoded
        }

        val at = body.lastIndexOf('@')
        require(at > 0 && at < body.lastIndex) {
            "قالب لینک Shadowsocks قابل تشخیص نیست"
        }

        var credentials = body.substring(0, at)
        val serverPart = body.substring(at + 1)
        if (!credentials.contains(':')) {
            credentials = decodeBase64(credentials) ?: credentials
        }

        val method = credentials.substringBefore(':').urlDecode()
        val password = credentials.substringAfter(':', "").urlDecode()
        require(method.isNotBlank() && password.isNotBlank()) {
            "روش رمزنگاری یا رمز Shadowsocks خالی است"
        }

        val serverUri = URI("ss://$serverPart")
        val host = serverUri.host
            ?: throw IllegalArgumentException("آدرس سرور Shadowsocks نامعتبر است")
        val port = serverUri.port
        require(port in 1..65535) { "پورت Shadowsocks نامعتبر است" }

        return JSONObject()
            .put("type", "shadowsocks")
            .put("tag", tag)
            .put("server", host)
            .put("server_port", port)
            .put("method", method)
            .put("password", password)
    }

    private fun addTls(
        outbound: JSONObject,
        query: Map<String, String>,
        host: String,
        scheme: String
    ) {
        val security = query["security"].orEmpty().lowercase()
        val reality = security == "reality" ||
            query.containsKey("pbk") || query.containsKey("publickey")
        val enabled = scheme == "trojan" ||
            security == "tls" || security == "reality" ||
            query["tls"] == "1"

        if (!enabled) return

        val tls = JSONObject()
            .put("enabled", true)
            .put("server_name", query["sni"] ?: query["servername"] ?: host)

        query["alpn"]?.split(',')?.filter { it.isNotBlank() }?.let {
            tls.put("alpn", JSONArray(it))
        }

        query["fp"]?.takeIf { it.isNotBlank() }?.let {
            tls.put("utls", JSONObject().put("enabled", true).put("fingerprint", it))
        }

        if (reality) {
            val publicKey = query["pbk"] ?: query["publickey"]
            require(!publicKey.isNullOrBlank()) {
                "کلید عمومی Reality (pbk) در لینک وجود ندارد"
            }
            tls.put("reality", JSONObject()
                .put("enabled", true)
                .put("public_key", publicKey)
                .put("short_id", query["sid"] ?: query["shortid"].orEmpty()))
        }
        outbound.put("tls", tls)
    }

    private fun addTransport(outbound: JSONObject, query: Map<String, String>) {
        when (query["type"]?.lowercase()) {
            "ws" -> {
                val transport = JSONObject()
                    .put("type", "ws")
                    .put("path", query["path"] ?: "/")
                val host = query["host"] ?: query["ws-host"]
                if (!host.isNullOrBlank()) {
                    transport.put("headers", JSONObject().put("Host", host))
                }
                outbound.put("transport", transport)
            }
            "grpc" -> outbound.put("transport", JSONObject()
                .put("type", "grpc")
                .put("service_name", query["serviceName"] ?: query["servicename"] ?: query["path"].orEmpty()))
            "httpupgrade" -> outbound.put("transport", JSONObject()
                .put("type", "httpupgrade")
                .put("path", query["path"] ?: "/"))
            "tcp", null, "" -> Unit
            else -> throw IllegalArgumentException(
                "نوع انتقال پشتیبانی‌نشده: ${query["type"]}"
            )
        }
    }

    private fun parseQuery(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split('&').mapNotNull { part ->
            if (part.isBlank()) return@mapNotNull null
            val key = part.substringBefore('=').urlDecode().lowercase()
            val value = part.substringAfter('=', "").urlDecode()
            key to value
        }.toMap()
    }

    private fun String.urlDecode(): String =
        try {
            URLDecoder.decode(this, StandardCharsets.UTF_8.name())
        } catch (_: Exception) {
            this
        }

    private fun decodeBase64(value: String): String? {
        val normalized = value.trim()
            .replace('-', '+')
            .replace('_', '/')
            .let { it + "=".repeat((4 - it.length % 4) % 4) }
        val flags = intArrayOf(Base64.DEFAULT, Base64.NO_WRAP, Base64.URL_SAFE or Base64.NO_WRAP)
        for (flag in flags) {
            try {
                val decoded = Base64.decode(normalized, flag)
                val text = String(decoded, StandardCharsets.UTF_8).trim()
                if (text.isNotBlank()) return text
            } catch (_: Exception) {
            }
        }
        return null
    }
}
