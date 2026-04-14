package com.android.cheburgate.core

import com.android.cheburgate.data.model.Server
import org.json.JSONArray
import org.json.JSONObject

object ConfigBuilder {

    fun build(server: Server, proxyPort: Int, proxyToken: String): String {
        val root = JSONObject()

        root.put("log", JSONObject().put("level", "warn"))

        root.put("dns", JSONObject().put("servers", JSONArray().put(
            JSONObject().put("tag", "dns").put("type", "udp").put("server", "8.8.8.8")
        )))

        root.put("inbounds", JSONArray().put(
            JSONObject().apply {
                put("type", "http")
                put("listen", "127.0.0.1")
                put("listen_port", proxyPort)
                put("sniff", true)
                put("sniff_override_destination", false)
                put("users", JSONArray().put(
                    JSONObject().put("username", proxyToken).put("password", proxyToken)
                ))
            }
        ))

        root.put("outbounds", JSONArray().apply {
            put(buildOutbound(server))
            put(JSONObject().put("tag", "direct").put("type", "direct"))
        })

        root.put("route", JSONObject().apply {
            put("rules", JSONArray().apply {
                put(JSONObject().put("ip_cidr", JSONArray().put("8.8.8.8/32")).put("action", "direct"))
                put(JSONObject().put("ip_is_private", true).put("action", "direct"))
            })
            put("final", "proxy")
        })

        // org.json экранирует "/" как "\/" — убираем
        return root.toString(2).replace("\\/", "/")
    }

    private fun buildOutbound(server: Server): JSONObject {
        return when (server.protocol.lowercase()) {
            "vless"     -> buildVlessOutbound(server)
            "hysteria2" -> buildHysteria2Outbound(server)
            else -> throw IllegalArgumentException("Unsupported protocol: ${server.protocol}")
        }
    }

    /**
     * xhttp HTTP/1.1 не выполняет TLS handshake в sing-box-extended
     * (client.go: dialContext делает tls.ClientHandshake только для HTTP/2).
     * Поэтому http/1.1 и пустой ALPN заменяем на h2. h3 оставляем как есть.
     */
    private fun fixXhttpAlpn(alpn: String?): String {
        val lower = alpn?.lowercase()?.trim()
        return if (lower.isNullOrEmpty() || lower == "http/1.1") "h2" else lower
    }

    private fun buildVlessOutbound(server: Server): JSONObject {
        val transport = server.transport?.lowercase() ?: "tcp"

        val obj = JSONObject().apply {
            put("type", "vless")
            put("tag", "proxy")
            put("server", server.address)
            put("server_port", server.port)
            put("uuid", server.uuid ?: "")
            if (!server.flow.isNullOrEmpty()) put("flow", server.flow)
        }

        val security = server.security?.lowercase() ?: "none"
        if (security == "reality" || security == "tls") {
            val tls = JSONObject().apply {
                put("enabled", true)
                if (security == "tls") put("insecure", server.insecure)
                if (security == "reality") put("insecure", false)
                if (!server.serverName.isNullOrEmpty()) put("server_name", server.serverName)
                val effectiveAlpn = if (transport == "xhttp") fixXhttpAlpn(server.alpn) else server.alpn
                if (!effectiveAlpn.isNullOrEmpty()) {
                    val alpnArray = JSONArray()
                    effectiveAlpn.split(",").forEach { alpnArray.put(it.trim()) }
                    put("alpn", alpnArray)
                }
                put("utls", JSONObject()
                    .put("enabled", true)
                    .put("fingerprint", server.fingerprint ?: "chrome"))
                if (security == "reality") {
                    put("reality", JSONObject().apply {
                        put("enabled", true)
                        put("public_key", server.publicKey ?: "")
                        put("short_id", server.shortId ?: "")
                    })
                }
            }
            obj.put("tls", tls)
        }

        if (transport != "tcp") {
            val tr = JSONObject().put("type", transport)
            if (!server.path.isNullOrEmpty()) tr.put("path", server.path)
            if (transport == "xhttp") {
                if (!server.xhttpHost.isNullOrEmpty()) tr.put("host", server.xhttpHost)
                tr.put("mode", server.xhttpMode ?: "auto")
                tr.put("x_padding_bytes", "100-1000")

                // Download секция не генерируется: upload и download оба через h2.
                // h3 download требует QUIC (UDP) который может быть заблокирован,
                // и создаёт отдельное соединение через detour что усложняет конфиг.
            }
            obj.put("transport", tr)
        }

        // xhttp: packet_encoding пустая строка (как в официальном примере)
        if (transport == "xhttp") {
            obj.put("packet_encoding", "")
        }

        return obj
    }

    private fun buildHysteria2Outbound(server: Server): JSONObject {
        val obj = JSONObject().apply {
            put("type", "hysteria2")
            put("tag", "proxy")
            put("server", server.address)
            put("server_port", server.port)
            put("password", server.password ?: "")
        }

        if (!server.obfsType.isNullOrEmpty()) {
            obj.put("obfs", JSONObject().apply {
                put("type", server.obfsType)
                put("password", server.obfsPassword ?: "")
            })
        }

        obj.put("tls", JSONObject().apply {
            put("enabled", true)
            if (!server.serverName.isNullOrEmpty()) put("server_name", server.serverName)
            put("insecure", server.insecure)
        })

        return obj
    }
}
