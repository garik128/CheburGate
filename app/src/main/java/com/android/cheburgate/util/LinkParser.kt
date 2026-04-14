package com.android.cheburgate.util

import android.net.Uri
import com.android.cheburgate.data.model.Server
import org.json.JSONObject

object LinkParser {

    fun parse(link: String): Server? = try {
        val trimmed = link.trim()
        when {
            trimmed.startsWith("vless://") -> parseVless(trimmed)
            trimmed.startsWith("hy2://") || trimmed.startsWith("hysteria2://") -> parseHy2(trimmed)
            else -> null
        }
    } catch (_: Exception) {
        null
    }

    private fun parseVless(link: String): Server {
        val uri = Uri.parse(link)
        val uuid = uri.userInfo ?: ""
        val host = uri.host ?: ""
        val port = uri.port.takeIf { it > 0 } ?: 443
        val name = Uri.decode(uri.fragment ?: "")

        val type = uri.getQueryParameter("type") ?: "tcp"
        val security = uri.getQueryParameter("security") ?: "none"
        val flow = uri.getQueryParameter("flow")
        val publicKey = uri.getQueryParameter("pbk")
        val shortId = uri.getQueryParameter("sid")
        val sni = uri.getQueryParameter("sni")
        val fp = uri.getQueryParameter("fp") ?: "chrome"
        val path = uri.getQueryParameter("path")
        val xhttpHost = uri.getQueryParameter("host")
        val xhttpMode = uri.getQueryParameter("mode")
        val alpn = uri.getQueryParameter("alpn")
        val insecureStr = uri.getQueryParameter("insecure")
            ?: uri.getQueryParameter("allowInsecure")
        val insecure = insecureStr == "1" || insecureStr.equals("true", ignoreCase = true)

        // Парсим extra для download ALPN (xray формат downloadSettings)
        var downloadAlpn: String? = null
        val extra = uri.getQueryParameter("extra")
        if (extra != null && type == "xhttp") {
            try {
                val extraJson = JSONObject(extra)
                val dlSettings = extraJson.optJSONObject("downloadSettings")
                val tlsSettings = dlSettings?.optJSONObject("tlsSettings")
                val dlAlpnArray = tlsSettings?.optJSONArray("alpn")
                if (dlAlpnArray != null && dlAlpnArray.length() > 0) {
                    downloadAlpn = dlAlpnArray.getString(0)
                }
            } catch (_: Exception) { }
        }

        return Server(
            name = name.ifEmpty { host },
            protocol = "vless",
            address = host,
            port = port,
            uuid = uuid,
            password = null,
            flow = flow,
            transport = type,
            security = security,
            publicKey = publicKey,
            shortId = shortId,
            serverName = sni,
            fingerprint = fp,
            path = path,
            insecure = insecure,
            xhttpHost = xhttpHost,
            xhttpMode = xhttpMode,
            alpn = alpn,
            downloadAlpn = downloadAlpn
        )
    }

    private fun parseHy2(link: String): Server {
        val uri = Uri.parse(link)
        val password = uri.userInfo ?: ""
        val host = uri.host ?: ""
        val port = uri.port.takeIf { it > 0 } ?: 443
        val name = Uri.decode(uri.fragment ?: "")

        val sni = uri.getQueryParameter("sni")
        val insecureStr = uri.getQueryParameter("insecure")
            ?: uri.getQueryParameter("allow_insecure")
        val insecure = insecureStr == "1" || insecureStr.equals("true", ignoreCase = true)

        val obfsType = uri.getQueryParameter("obfs")
        val obfsPassword = uri.getQueryParameter("obfs-password")

        return Server(
            name = name.ifEmpty { host },
            protocol = "hysteria2",
            address = host,
            port = port,
            uuid = null,
            password = password,
            flow = null,
            transport = null,
            security = "tls",
            publicKey = null,
            shortId = null,
            serverName = sni,
            fingerprint = null,
            path = null,
            insecure = insecure,
            obfsType = obfsType,
            obfsPassword = obfsPassword
        )
    }

    fun toLink(server: Server): String = when (server.protocol.lowercase()) {
        "vless" -> toVlessLink(server)
        "hysteria2" -> toHy2Link(server)
        else -> throw IllegalArgumentException("Unsupported protocol: ${server.protocol}")
    }

    private fun toVlessLink(server: Server): String {
        val builder = Uri.Builder()
            .scheme("vless")
            .encodedAuthority("${Uri.encode(server.uuid ?: "")}@${server.address}:${server.port}")

        val transport = server.transport ?: "tcp"
        builder.appendQueryParameter("type", transport)

        val security = server.security ?: "none"
        builder.appendQueryParameter("security", security)

        server.flow?.let { builder.appendQueryParameter("flow", it) }
        server.publicKey?.let { builder.appendQueryParameter("pbk", it) }
        server.shortId?.let { builder.appendQueryParameter("sid", it) }
        server.serverName?.let { builder.appendQueryParameter("sni", it) }
        server.fingerprint?.let { builder.appendQueryParameter("fp", it) }
        server.path?.let { builder.appendQueryParameter("path", it) }
        server.alpn?.let { builder.appendQueryParameter("alpn", it) }

        builder.fragment(server.name)
        return builder.build().toString()
    }

    private fun toHy2Link(server: Server): String {
        val builder = Uri.Builder()
            .scheme("hy2")
            .encodedAuthority("${Uri.encode(server.password ?: "")}@${server.address}:${server.port}")

        server.serverName?.let { builder.appendQueryParameter("sni", it) }
        if (server.insecure) builder.appendQueryParameter("insecure", "1")

        builder.fragment(server.name)
        return builder.build().toString()
    }

    fun parseMultiple(text: String): List<Server> {
        return text.lines()
            .mapNotNull { parse(it.trim()) }
    }
}
