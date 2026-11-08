package com.miniphoto.viewer.widget

import android.content.Context

internal class PhotoWidgetStateStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun photoIndex(appWidgetId: Int): Int = preferences.getInt(indexKey(appWidgetId), 0)

    fun setPhotoIndex(appWidgetId: Int, index: Int) {
        preferences.edit().putInt(indexKey(appWidgetId), index).apply()
    }

    fun controlsVisible(appWidgetId: Int): Boolean =
        preferences.getBoolean(controlsVisibleKey(appWidgetId), false)

    fun setControlsVisible(appWidgetId: Int, visible: Boolean) {
        preferences.edit().putBoolean(controlsVisibleKey(appWidgetId), visible).apply()
    }

    fun remove(appWidgetId: Int) {
        preferences.edit()
            .remove(indexKey(appWidgetId))
            .remove(controlsVisibleKey(appWidgetId))
            .apply()
    }

    private fun indexKey(appWidgetId: Int): String = "photo_index_$appWidgetId"

    private fun controlsVisibleKey(appWidgetId: Int): String = "controls_visible_$appWidgetId"

    private companion object {
        const val PREFERENCES_NAME = "photo_widget_state"
    }
}
