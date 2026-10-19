package com.schwartzinc.sunday;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "WidgetBridge")
public class WidgetBridgePlugin extends Plugin {
    public static final String PREFS_NAME = "sunday_widget";
    public static final String KEY_SNAPSHOT = "snapshot";
    public static final String KEY_SYNCING_SINCE = "syncing_since";
    public static final String KEY_SYNC_ERROR = "sync_error";
    private static final String TAG = "SundayWidget";

    @Override
    public void load() {
        super.load();
        // Clear any stale syncing state on fresh process start (keeps a previous error flag)
        writeSyncing(getContext(), false, null);
        dispatchIntentIfPresent(getActivity().getIntent());
    }

    @Override
    protected void handleOnNewIntent(Intent intent) {
        super.handleOnNewIntent(intent);
        dispatchIntentIfPresent(intent);
    }

    private static PendingIntent getTimeoutPendingIntent(Context context) {
        Intent intent = new Intent(context, SundayWidgetProvider.class);
        intent.setAction(SundayWidgetProvider.ACTION_SYNC_TIMEOUT);
        return PendingIntent.getBroadcast(
            context,
            3,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    // error: null leaves the error flag untouched when syncing stops; starting a sync always clears it.
    private static void writeSyncing(Context context, boolean syncing, Boolean error) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent timeoutPending = getTimeoutPendingIntent(context);
        Log.d(TAG, "writeSyncing syncing=" + syncing + " error=" + error);

        if (syncing) {
            prefs.edit()
                .putLong(KEY_SYNCING_SINCE, System.currentTimeMillis())
                .remove(KEY_SYNC_ERROR)
                .commit();
            if (alarmManager != null) {
                // Safety net: in case the app process/WebView dies or freezes before clearing
                // the syncing state, reset the widget to idle after ~61s.
                alarmManager.set(
                    AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + 61_000L,
                    timeoutPending
                );
            }
        } else {
            SharedPreferences.Editor editor = prefs.edit().remove(KEY_SYNCING_SINCE);
            if (error != null) {
                if (error) {
                    editor.putBoolean(KEY_SYNC_ERROR, true);
                } else {
                    editor.remove(KEY_SYNC_ERROR);
                }
            }
            editor.commit();
            if (alarmManager != null) {
                alarmManager.cancel(timeoutPending);
            }
        }
        SundayWidgetProvider.refreshAll(context);
    }

    private void dispatchIntentIfPresent(Intent intent) {
        if (intent == null) return;
        String action = intent.getStringExtra("widgetAction");
        if (action == null || action.isEmpty()) return;

        // When triggered by widget sync:
        // 1. Keep the activity in foreground so Android doesn't freeze the WebView/network.
        //    JS calls closeApp() when sync completes (or fails).
        // 2. If already syncing, drop duplicate dispatch (the tap just brings the app to front).
        // 3. Otherwise immediately mark syncing=true so the widget shows the spinner right away.
        if ("sync".equals(action)) {
            SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            if (SundayWidgetProvider.isSyncing(prefs)) {
                // Drop duplicate sync request
                intent.removeExtra("widgetAction");
                intent.removeExtra("taskId");
                intent.removeExtra("columnId");
                return;
            }

            writeSyncing(getContext(), true, null);
        }

        JSObject data = new JSObject();
        data.put("widgetAction", action);
        data.put("taskId", intent.getStringExtra("taskId"));
        data.put("columnId", intent.getStringExtra("columnId"));

        // Clear extras so recreation/relaunch doesn't re-trigger
        intent.removeExtra("widgetAction");
        intent.removeExtra("taskId");
        intent.removeExtra("columnId");

        // retainUntilConsumed = true preserves this until the JS listener registers (cold start)
        notifyListeners("widgetAction", data, true);
    }

    @PluginMethod
    public void update(PluginCall call) {
        String snapshot = call.getString("snapshot");
        if (snapshot == null) {
            call.reject("Missing snapshot parameter");
            return;
        }

        Context ctx = getContext();
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_SNAPSHOT, snapshot).apply();

        SundayWidgetProvider.refreshAll(ctx);
        call.resolve();
    }

    @PluginMethod
    public void setSyncing(PluginCall call) {
        boolean syncing = Boolean.TRUE.equals(call.getBoolean("syncing"));
        writeSyncing(getContext(), syncing, call.getBoolean("error"));
        call.resolve();
    }

    @PluginMethod
    public void closeApp(PluginCall call) {
        String message = call.getString("message");
        call.resolve();
        getActivity().runOnUiThread(() -> {
            if (message != null && !message.isEmpty()) {
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            }
            getActivity().finishAndRemoveTask();
        });
    }
}
