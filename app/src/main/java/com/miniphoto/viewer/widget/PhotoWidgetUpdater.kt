package com.miniphoto.viewer.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

object PhotoWidgetUpdater {
    fun requestUpdate(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val appWidgetIds = manager.getAppWidgetIds(
            ComponentName(context, PhotoWidgetProvider::class.java),
        )
        if (appWidgetIds.isEmpty()) return
        context.sendBroadcast(
            Intent(context, PhotoWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, appWidgetIds)
            },
        )
    }
}
