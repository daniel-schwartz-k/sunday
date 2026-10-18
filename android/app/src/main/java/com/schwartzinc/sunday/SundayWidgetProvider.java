package com.schwartzinc.sunday;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.widget.RemoteViews;
import org.json.JSONObject;

public class SundayWidgetProvider extends AppWidgetProvider {

    public static void refreshAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName widgetComponent = new ComponentName(context, SundayWidgetProvider.class);
        int[] widgetIds = manager.getAppWidgetIds(widgetComponent);
        if (widgetIds == null || widgetIds.length == 0) return;

        for (int id : widgetIds) {
            updateWidget(context, manager, id);
        }
        manager.notifyAppWidgetViewDataChanged(widgetIds, R.id.widget_list);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateWidget(context, appWidgetManager, appWidgetId);
        }
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetIds, R.id.widget_list);
    }

    private static void updateWidget(Context context, AppWidgetManager manager, int widgetId) {
        SharedPreferences prefs = context.getSharedPreferences(WidgetBridgePlugin.PREFS_NAME, Context.MODE_PRIVATE);
        String snapshotStr = prefs.getString(WidgetBridgePlugin.KEY_SNAPSHOT, null);
        String themeName = "dark";
        if (snapshotStr != null) {
            try {
                JSONObject snapshot = new JSONObject(snapshotStr);
                themeName = snapshot.optString("theme", "dark");
            } catch (Exception ignored) {}
        }
        WidgetTheme theme = WidgetTheme.get(themeName);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_root);

        // Apply theme colors to root & header
        views.setTextColor(R.id.widget_header_title, theme.textPrimary);
        views.setTextColor(R.id.widget_empty_text, theme.textSecondary);

        // Header click: open the main app
        Intent openAppIntent = new Intent(context, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent openAppPending = PendingIntent.getActivity(
            context,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.widget_header, openAppPending);

        // Bind ListView to RemoteViewsService
        Intent serviceIntent = new Intent(context, SundayWidgetService.class);
        serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        // Uri data makes the intent distinct so Android doesn't reuse service instances across widgets
        serviceIntent.setData(Uri.parse(serviceIntent.toUri(Intent.URI_INTENT_SCHEME)));
        views.setRemoteAdapter(R.id.widget_list, serviceIntent);
        views.setEmptyView(R.id.widget_list, R.id.widget_empty_text);

        // Template pending intent for list items (fill-in intents supply extras)
        Intent templateIntent = new Intent(context, MainActivity.class);
        templateIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent templatePending = PendingIntent.getActivity(
            context,
            widgetId,
            templateIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE
        );
        views.setPendingIntentTemplate(R.id.widget_list, templatePending);

        manager.updateAppWidget(widgetId, views);
    }
}
