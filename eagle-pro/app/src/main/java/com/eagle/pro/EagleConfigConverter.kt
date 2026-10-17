package com.eagle.pro

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

object EagleConfigConverter {

    fun convert(input: String): String {
        val value = input.trim()
        require(value.isNotEmpty()) { "کانفیگ خالی است" }

        if (value.startsWith("{")) {
            val json = JSONObject(value)
            require(json.has("outbounds")) { "بخش outbounds در JSON وجود ندارد" }
            require(json.has("inbounds")) { "بخش inbounds در JSON وجود ندارد" }
            return json.toString(2)
        }

        val outbound = when {
            value.startsWith("vmess://", true) -> vmess(value)
            value.startsWith("vless://", true) -> vless(value)
            value.startsWith("hysteria2://", true) ||
                value.startsWith("hy2://", true) -> hysteria2(value)
            value.startsWith("trojan://", true) -> trojan(value)
            value.startsWith("ss://", true) -> shadowsocks(value)
            else -> throw IllegalArgumentException("نوع لینک پشتیبانی نمی‌شود")
        }

        val tag = outbound.optString("tag", "proxy")
        outbound.put("tag", tag)

        val outbounds = JSONArray()
            .put(outbound)
            .put(JSONObject().put("type", "direct").put("tag", "direct"))
            .put(JSONObject().put("type", "block").put("tag", "block"))

        val dnsServer = JSONObject()
            .put("type", "udp")
            .put("tag", "remote-dns")
            .put("server", "1.1.1.1")
            .put("server_port", 53)
            .put("detour", tag)

        val dns = JSONObject()
            .put("servers", JSONArray().put(dnsServer))
            .put("final", "remote-dns")

        val tun = JSONObject()
            .put("type", "tun")
            .put("tag", "tun-in")
            .put("interface_name", "eagle-tun")
            .put("address", JSONArray().put("172.19.0.1/30"))
            .put("mtu", 1400)
            .put("auto_route", true)
            .put("strict_route", true)
            .put("stack", "system")

        val route = JSONObject()
            .put("auto_detect_interface", true)
            .put("final", tag)

        return JSONObject()
            .put("log", JSONObject().put("level", "info"))
            .put("dns", dns)
            .put("inbounds", JSONArray().put(tun))
            .put("outbounds", outbounds)
            .put("route", route)
            .toString(2)
    }

    private fun vless(value: String): JSONObject {
        val uri = URI(value)
        val uuid = decode(uri.rawUserInfo.orEmpty())
        require(uuid.isNotBlank()) { "شناسه UUID در لینک VLESS وجود ندارد" }
        val host = uri.host ?: throw IllegalArgumentException("آدرس سرور VLESS نامعتبر است")
        require(uri.port in 1..65535) { "پورت VLESS نامعتبر است" }
        val q = query(uri.rawQuery)
        val out = JSONObject()
            .put("type", "vless")
            .put("tag", "proxy")
            .put("server", host)
            .put("server_port", uri.port)
            .put("uuid", uuid)

        q["flow"]?.takeIf { it.isNotBlank() }?.let { out.put("flow", it) }

        val security = q["security"].orEmpty()
        if (security == "tls" || security == "reality") {
            val tls = JSONObject().put("enabled", true)
            q["sni"]?.takeIf { it.isNotBlank() }?.let { tls.put("server_name", it) }
            q["alpn"]?.takeIf { it.isNotBlank() }?.let {
                tls.put("alpn", JSONArray(it.split(",").map(String::trim)))
            }
            q["fp"]?.takeIf { it.isNotBlank() }?.let {
                tls.put("utls", JSONObject().put("enabled", true).put("fingerprint", it))
            }
            if (security == "reality") {
                val publicKey = q["pbk"].orEmpty()
                require(publicKey.isNotBlank()) { "کلید عمومی Reality (pbk) وجود ندارد" }
                tls.put(
                    "reality",
                    JSONObject()
                        .put("enabled", true)
                        .put("public_key", publicKey)
                        .put("short_id", q["sid"].orEmpty())
                )
            }
            out.put("tls", tls)
        }

        when (q["type"].orEmpty()) {
            "", "tcp" -> Unit
            "ws" -> {
                val transport = JSONObject().put("type", "ws")
                q["path"]?.takeIf { it.isNotBlank() }?.let { transport.put("path", it) }
                q["host"]?.takeIf { it.isNotBlank() }?.let {
                    transport.put("headers", JSONObject().put("Host", it))
                }
                out.put("transport", transport)
            }
            "grpc" -> {
                out.put(
                    "transport",
                    JSONObject()
                        .put("type", "grpc")
                        .put("service_name", q["serviceName"].orEmpty())
                )
            }
            "httpupgrade" -> {
                val transport = JSONObject().put("type", "httpupgrade")
                q["path"]?.takeIf { it.isNotBlank() }?.let { transport.put("path", it) }
                q["host"]?.takeIf { it.isNotBlank() }?.let { transport.put("host", it) }
                out.put("transport", transport)
            }
            else -> throw IllegalArgumentException("نوع انتقال VLESS پشتیبانی نمی‌شود: ${q["type"]}")
        }
        return out
    }

    private fun vmess(value: String): JSONObject {
        val encoded = value.removePrefix("vmess://").trim()
        val decoded = decodeBase64(encoded)
        val data = JSONObject(decoded)
        val host = data.optString("add")
        val port = data.optInt("port", 0)
        val uuid = data.optString("id")
        require(host.isNotBlank() && port in 1..65535 && uuid.isNotBlank()) {
            "اطلاعات سرور VMess ناقص است"
        }

        val out = JSONObject()
            .put("type", "vmess")
            .put("tag", "proxy")
            .put("server", host)
            .put("server_port", port)
            .put("uuid", uuid)
            .put("security", data.optString("scy", "auto").ifBlank { "auto" })

        val tlsMode = data.optString("tls")
        if (tlsMode.equals("tls", true)) {
            val tls = JSONObject().put("enabled", true)
            val sni = data.optString("sni").ifBlank { data.optString("host") }
            if (sni.isNotBlank()) tls.put("server_name", sni)
            out.put("tls", tls)
        }

        when (data.optString("net", "tcp")) {
            "", "tcp" -> Unit
            "ws" -> {
                val transport = JSONObject().put("type", "ws")
                val path = data.optString("path")
                if (path.isNotBlank()) transport.put("path", path)
                val wsHost = data.optString("host")
                if (wsHost.isNotBlank()) {
                    transport.put("headers", JSONObject().put("Host", wsHost))
                }
                out.put("transport", transport)
            }
            "grpc" -> out.put(
                "transport",
                JSONObject().put("type", "grpc").put("service_name", data.optString("path"))
            )
            else -> throw IllegalArgumentException("انتقال VMess پشتیبانی نمی‌شود: ${data.optString("net")}")
        }
        return out
    }

    private fun hysteria2(value: String): JSONObject {
        val uri = URI(value)
        val host = uri.host ?: throw IllegalArgumentException("آدرس سرور Hysteria2 نامعتبر است")
        require(uri.port in 1..65535) { "پورت Hysteria2 نامعتبر است" }
        val password = decode(uri.rawUserInfo.orEmpty())
        require(password.isNotBlank()) { "رمز Hysteria2 وجود ندارد" }
        val q = query(uri.rawQuery)
        val out = JSONObject()
            .put("type", "hysteria2")
            .put("tag", "proxy")
            .put("server", host)
            .put("server_port", uri.port)
            .put("password", password)

        val tls = JSONObject().put("enabled", true)
        q["sni"]?.takeIf { it.isNotBlank() }?.let { tls.put("server_name", it) }
        q["alpn"]?.takeIf { it.isNotBlank() }?.let {
            tls.put("alpn", JSONArray(it.split(",").map(String::trim))
            )
        }
        if (q["insecure"] == "1" || q["insecure"] == "true") {
            tls.put("insecure", true)
        }
        out.put("tls", tls)

        q["obfs"]?.takeIf { it.isNotBlank() }?.let { mode ->
            if (mode == "salamander") {
                val obfsPassword = q["obfs-password"].orEmpty()
                require(obfsPassword.isNotBlank()) { "رمز obfs در لینک وجود ندارد" }
                out.put("obfs", JSONObject().put("type", mode).put("password", obfsPassword))
            }
        }
        return out
    }

    private fun trojan(value: String): JSONObject {
        val uri = URI(value)
        val host = uri.host ?: throw IllegalArgumentException("آدرس سرور Trojan نامعتبر است")
        require(uri.port in 1..65535) { "پورت Trojan نامعتبر است" }
        val password = decode(uri.rawUserInfo.orEmpty())
        require(password.isNotBlank()) { "رمز Trojan وجود ندارد" }
        val q = query(uri.rawQuery)
        val tls = JSONObject().put("enabled", true)
        q["sni"]?.takeIf { it.isNotBlank() }?.let { tls.put("server_name", it) }
        q["insecure"]?.let { tls.put("insecure", it == "1" || it == "true") }
        return JSONObject()
            .put("type", "trojan")
            .put("tag", "proxy")
            .put("server", host)
            .put("server_port", uri.port)
            .put("password", password)
            .put("tls", tls)
    }

    private fun shadowsocks(value: String): JSONObject {
        val raw = value.removePrefix("ss://").substringBefore('#').substringBefore('?')
        val decoded = if (!raw.contains("@")) decodeBase64(raw) else raw
        val at = decoded.lastIndexOf('@')
        require(at > 0) { "فرمت لینک Shadowsocks نامعتبر است" }
        val credentials = decode(decoded.substring(0, at))
        val colon = credentials.indexOf(':')
        require(colon > 0) { "روش رمزنگاری یا رمز Shadowsocks نامعتبر است" }
        val method = credentials.substring(0, colon)
        val password = credentials.substring(colon + 1)
        val endpoint = URI("ss://${decoded.substring(at + 1)}")
        val host = endpoint.host ?: throw IllegalArgumentException("آدرس Shadowsocks نامعتبر است")
        require(endpoint.port in 1..65535) { "پورت Shadowsocks نامعتبر است" }
        return JSONObject()
            .put("type", "shadowsocks")
            .put("tag", "proxy")
            .put("server", host)
            .put("server_port", endpoint.port)
            .put("method", method)
            .put("password", password)
    }

    private fun query(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return raw.split("&").mapNotNull { item ->
            if (item.isBlank()) return@mapNotNull null
            val parts = item.split("=", limit = 2)
            decode(parts[0]) to decode(parts.getOrElse(1) { "" })
        }.toMap()
    }

    private fun decode(value: String): String =
        URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8.name())

    private fun decodeBase64(value: String): String {
        val normalized = value.replace('-', '+').replace('_', '/')
        val padded = normalized + "=".repeat((4 - normalized.length % 4) % 4)
        val bytes = try {
            Base64.decode(padded, Base64.DEFAULT)
        } catch (_: IllegalArgumentException) {
            Base64.decode(padded, Base64.URL_SAFE)
        }
        return String(bytes, StandardCharsets.UTF_8)
    }
}
