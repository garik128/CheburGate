package com.android.cheburgate.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.widget.RemoteViews
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.android.cheburgate.R
import com.android.cheburgate.data.db.AppDatabase
import com.android.cheburgate.data.model.ServiceItem
import com.android.cheburgate.ui.browser.BrowserActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CheburWidget : AppWidgetProvider() {

    companion object {
        private val ICON_IDS = intArrayOf(
            R.id.widgetIcon1, R.id.widgetIcon2, R.id.widgetIcon3, R.id.widgetIcon4
        )

        fun update(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            CoroutineScope(Dispatchers.IO).launch {
                val db = AppDatabase.getInstance(context)
                val services = db.serviceDao().getVisibleFlow().first().take(4)

                val views = RemoteViews(context.packageName, R.layout.widget_cheburgate)
                val loader = ImageLoader(context)

                services.forEachIndexed { index, item ->
                    val iconId = ICON_IDS[index]
                    loadIcon(context, loader, item, views, iconId)

                    val intent = Intent(context, BrowserActivity::class.java).apply {
                        putExtra(BrowserActivity.EXTRA_URL, item.url)
                        putExtra(BrowserActivity.EXTRA_SERVICE_NAME, item.name)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    val pendingIntent = PendingIntent.getActivity(
                        context,
                        index,
                        intent,
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    views.setOnClickPendingIntent(iconId, pendingIntent)
                }

                appWidgetManager.updateAppWidget(appWidgetId, views)
            }
        }

        private suspend fun loadIcon(
            context: Context,
            loader: ImageLoader,
            item: ServiceItem,
            views: RemoteViews,
            iconId: Int
        ) {
            val host = android.net.Uri.parse(item.url).host ?: return
            val iconUrl = "https://icons.duckduckgo.com/ip3/$host.ico"

            val result = loader.execute(
                ImageRequest.Builder(context)
                    .data(iconUrl)
                    .allowHardware(false)
                    .build()
            )

            val iconSizePx = (context.resources.displayMetrics.density * 48).toInt()

            val bitmap = if (result is SuccessResult) {
                val drawable = result.drawable
                val raw = if (drawable is android.graphics.drawable.BitmapDrawable) {
                    drawable.bitmap
                } else {
                    val w = drawable.intrinsicWidth.coerceAtLeast(1)
                    val h = drawable.intrinsicHeight.coerceAtLeast(1)
                    Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
                        val canvas = android.graphics.Canvas(bmp)
                        drawable.setBounds(0, 0, w, h)
                        drawable.draw(canvas)
                    }
                }
                // Масштабируем до нормального размера — favicons часто 16x16 или 32x32
                if (raw.width >= iconSizePx) raw
                else Bitmap.createScaledBitmap(raw, iconSizePx, iconSizePx, true)
            } else null

            if (bitmap != null) {
                views.setImageViewBitmap(iconId, bitmap)
            } else {
                views.setImageViewResource(iconId, R.drawable.ic_service_default)
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { id ->
            update(context, appWidgetManager, id)
        }
    }
}
