package com.android.cheburgate.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "services")
data class ServiceItem(
    @PrimaryKey val id: String,   // "telegram", "youtube", etc.
    val name: String,
    val url: String,              // из него берётся host для иконки
    val isBuiltIn: Boolean,
    val isVisible: Boolean = true,
    val sortOrder: Int = 0
) {
    companion object {
        val BUILT_IN = listOf(
            ServiceItem("telegram",   "Telegram",    "https://web.telegram.org",  true, true, 0),
            ServiceItem("youtube",    "YouTube",     "https://youtube.com",        true, true, 1),
            ServiceItem("whatsapp",   "WhatsApp",    "https://web.whatsapp.com",  true, true, 2),
            ServiceItem("instagram",  "Instagram",   "https://instagram.com",     true, true, 3),
            ServiceItem("twitter",    "X (Twitter)", "https://x.com",             true, true, 4),
            ServiceItem("facebook",   "Facebook",    "https://facebook.com",      true, true, 5),
            ServiceItem("tiktok",     "TikTok",      "https://tiktok.com",        true, true, 6),
            ServiceItem("google",     "Google",      "https://google.com",        true, true, 7),
            ServiceItem("reddit",     "Reddit",      "https://reddit.com",        true, true, 8),
            ServiceItem("linkedin",   "LinkedIn",    "https://linkedin.com",      true, true, 9),
            
        )
    }
}
