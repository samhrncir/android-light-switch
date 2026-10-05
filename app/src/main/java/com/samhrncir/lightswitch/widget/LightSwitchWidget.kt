package com.samhrncir.lightswitch.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import com.samhrncir.lightswitch.MainActivity
import com.samhrncir.lightswitch.R
import com.samhrncir.lightswitch.ThemeController

/**
 * Home screen widget: a tap-to-flip light switch.
 *
 * The artwork comes from `drawable/widget_switch` (lever up, light room) and its
 * `drawable-night` twin (lever down, dark room). Launchers re-inflate widgets when
 * the system theme changes, so the picture follows the real theme without the app
 * having to listen for configuration changes it cannot receive in the background.
 */
class LightSwitchWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { update(context, appWidgetManager, it) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_TOGGLE) {
            val wantDark = !ThemeController.isSystemDark(context)
            val result = ThemeController.setSystemDark(context, wantDark)
            // The theme flips asynchronously; refresh once it has had time to land so
            // launchers that don't re-inflate on their own still show the right lever.
            val pendingResult = goAsync()
            Handler(Looper.getMainLooper()).postDelayed({
                updateAll(context)
                pendingResult.finish()
            }, if (result == ThemeController.Result.APPLIED) 900L else 0L)
            return
        }
        super.onReceive(context, intent)
    }

    companion object {
        private const val ACTION_TOGGLE = "com.samhrncir.lightswitch.action.TOGGLE_THEME"

        /** Re-renders every placed widget. Call after the permission state changes. */
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, LightSwitchWidget::class.java))
            ids.forEach { update(context, manager, it) }
        }

        private fun update(context: Context, manager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_light_switch)
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

            // With the permission, a tap flips the theme in place. Without it, a tap opens
            // the app so the user lands on the setup card.
            val onClick = if (ThemeController.hasWriteSecureSettings(context)) {
                PendingIntent.getBroadcast(
                    context,
                    0,
                    Intent(context, LightSwitchWidget::class.java).setAction(ACTION_TOGGLE),
                    flags,
                )
            } else {
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, MainActivity::class.java),
                    flags,
                )
            }
            views.setOnClickPendingIntent(R.id.widget_root, onClick)

            val dark = ThemeController.isSystemDark(context)
            views.setContentDescription(
                R.id.widget_switch,
                context.getString(if (dark) R.string.a11y_state_off else R.string.a11y_state_on),
            )
            manager.updateAppWidget(appWidgetId, views)
        }
    }
}
