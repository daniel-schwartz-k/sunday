package com.schwartzinc.sunday;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Build;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;
import org.json.JSONObject;

public class SundayWidgetProvider extends AppWidgetProvider {

    public static final String ACTION_SYNC_DISABLED = "com.schwartzinc.sunday.SYNC_DISABLED";
    public static final String ACTION_SYNC_TIMEOUT = "com.schwartzinc.sunday.SYNC_TIMEOUT";

    public static boolean isSyncing(SharedPreferences prefs) {
        long syncingSince = prefs.getLong(WidgetBridgePlugin.KEY_SYNCING_SINCE, 0L);
        return syncingSince > 0 && (System.currentTimeMillis() - syncingSince < 60_000L);
    }

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
    public void onReceive(Context context, Intent intent) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_SYNC_DISABLED.equals(action)) {
                Toast.makeText(context, "Sync is only available in AirTable mode", Toast.LENGTH_SHORT).show();
                return;
            }
            if (ACTION_SYNC_TIMEOUT.equals(action)) {
                SharedPreferences prefs = context.getSharedPreferences(WidgetBridgePlugin.PREFS_NAME, Context.MODE_PRIVATE);
                if (!isSyncing(prefs)) {
                    prefs.edit().remove(WidgetBridgePlugin.KEY_SYNCING_SINCE).commit();
                    refreshAll(context);
                }
                return;
            }
        }
        super.onReceive(context, intent);
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
        boolean syncEnabled = false;
        if (snapshotStr != null) {
            try {
                JSONObject snapshot = new JSONObject(snapshotStr);
                themeName = snapshot.optString("theme", "dark");
                syncEnabled = snapshot.optBoolean("syncEnabled", false);
            } catch (Exception ignored) {}
        }
        WidgetTheme theme = WidgetTheme.get(themeName);

        boolean isSyncing = isSyncing(prefs);

        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_root);

        // Apply theme colors to root, background, divider & header
        views.setImageViewResource(R.id.widget_bg_image, theme.bgRes);
        views.setInt(R.id.widget_header_divider, "setBackgroundColor", theme.border);
        views.setTextColor(R.id.widget_header_title, theme.textPrimary);
        views.setTextColor(R.id.widget_empty_text, theme.textSecondary);

        // Prepare pending intent for sync button / spinner area
        PendingIntent syncPending;
        if (syncEnabled) {
            Intent syncIntent = new Intent(context, MainActivity.class);
            syncIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_ANIMATION);
            syncIntent.putExtra("widgetAction", "sync");
            syncPending = PendingIntent.getActivity(
                context,
                1,
                syncIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
        } else {
            Intent disabledIntent = new Intent(context, SundayWidgetProvider.class);
            disabledIntent.setAction(ACTION_SYNC_DISABLED);
            syncPending = PendingIntent.getBroadcast(
                context,
                2,
                disabledIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
        }

        // Configure sync button / spinner
        if (isSyncing) {
            views.setViewVisibility(R.id.widget_sync_btn, View.GONE);
            views.setViewVisibility(R.id.widget_sync_progress, View.VISIBLE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                views.setColorStateList(
                    R.id.widget_sync_progress,
                    "setIndeterminateTintList",
                    ColorStateList.valueOf(theme.accent)
                );
            }
            // Keep click handler active even while showing spinner: brings the app to front,
            // or starts a fresh sync if the 60s timeout elapsed without a redraw.
            views.setOnClickPendingIntent(R.id.widget_sync_area, syncPending);
        } else {
            views.setViewVisibility(R.id.widget_sync_progress, View.GONE);
            views.setViewVisibility(R.id.widget_sync_btn, View.VISIBLE);

            if (syncEnabled) {
                views.setTextColor(R.id.widget_sync_btn, theme.accent);
            } else {
                int disabledColor = (theme.textSecondary & 0x00FFFFFF) | 0x66000000;
                views.setTextColor(R.id.widget_sync_btn, disabledColor);
            }
            views.setOnClickPendingIntent(R.id.widget_sync_area, syncPending);
        }

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
