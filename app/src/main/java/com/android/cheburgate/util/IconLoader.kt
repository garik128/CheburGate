package com.android.cheburgate.util

import android.content.Context
import android.graphics.drawable.Drawable
import android.net.Uri
import android.widget.ImageView
import coil.load
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.android.cheburgate.R

object IconLoader {

    /**
     * Загружает иконку в ImageView.
     * Цепочка источников (от высокого разрешения к низкому):
     * 1. Google faviconV2 256px  — высокое качество, часто отдаёт PWA-иконку
     * 2. apple-touch-icon.png    — 180x180, есть у большинства сайтов
     * 3. Google S2 128px         — стабильный fallback
     * 4. DuckDuckGo ico          — последний резерв
     * 5. ic_service_default      — если всё упало
     */
    fun load(imageView: ImageView, url: String) {
        val host = Uri.parse(url).host ?: run {
            imageView.setImageResource(R.drawable.ic_service_default)
            return
        }
        val sources = buildSources(host)
        loadChain(imageView, sources, 0)
    }

    private fun loadChain(imageView: ImageView, sources: List<String>, index: Int) {
        if (index >= sources.size) {
            imageView.setImageResource(R.drawable.ic_service_default)
            return
        }
        imageView.load(sources[index]) {
            crossfade(true)
            placeholder(R.drawable.ic_service_default)
            diskCachePolicy(CachePolicy.ENABLED)
            memoryCachePolicy(CachePolicy.ENABLED)
            // Не ставим error(default) — вместо этого fallback на следующий источник
            listener(
                onError = { _, _ ->
                    loadChain(imageView, sources, index + 1)
                }
            )
        }
    }

    suspend fun loadDrawable(context: Context, url: String): Drawable {
        val host = Uri.parse(url).host
            ?: return defaultDrawable(context)

        val loader = coil.Coil.imageLoader(context)
        for (src in buildSources(host)) {
            val result = loader.execute(
                ImageRequest.Builder(context).data(src).allowHardware(false).build()
            )
            if (result is SuccessResult) return result.drawable
        }
        return defaultDrawable(context)
    }

    private fun buildSources(host: String): List<String> = listOf(
        // Google faviconV2 — отдаёт лучшую иконку сайта до 256px (PWA-манифест, apple-touch-icon и т.д.)
        "https://t2.gstatic.com/faviconV2?client=SOCIAL&type=FAVICON" +
            "&fallback_opts=TYPE,SIZE,URL&url=https://$host&size=256",
        // apple-touch-icon — стандартный путь PWA-иконки 180×180
        "https://$host/apple-touch-icon.png",
        // Google S2 — надёжный fallback, 128px
        "https://www.google.com/s2/favicons?sz=128&domain=$host",
        // DuckDuckGo — последний резерв
        "https://icons.duckduckgo.com/ip3/$host.ico"
    )

    private fun defaultDrawable(context: Context): Drawable =
        context.getDrawable(R.drawable.ic_service_default)!!
}
