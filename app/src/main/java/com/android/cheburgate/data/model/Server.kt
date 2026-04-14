package com.android.cheburgate.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "servers")
data class Server(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val protocol: String,       // "vless" | "hysteria2"
    val address: String,
    val port: Int,
    val uuid: String?,          // vless
    val password: String?,      // hysteria2
    val flow: String?,
    val transport: String?,     // tcp, xhttp, ws, grpc
    val security: String?,      // reality, tls, none
    val publicKey: String?,     // reality
    val shortId: String?,       // reality
    val serverName: String?,
    val fingerprint: String?,
    val path: String?,
    val insecure: Boolean = false,
    val obfsType: String? = null,      // hysteria2 obfs type, напр. "salamander"
    val obfsPassword: String? = null,  // hysteria2 obfs password
    val xhttpHost: String? = null,     // xhttp HTTP Host header (из ?host=)
    val xhttpMode: String? = null,     // xhttp mode (из ?mode=): auto, stream-up, packet-up
    val alpn: String? = null,           // TLS ALPN, напр. "http/1.1" или "h2,http/1.1"
    val downloadAlpn: String? = null,   // xhttp: ALPN для download секции (h3, h2)
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
