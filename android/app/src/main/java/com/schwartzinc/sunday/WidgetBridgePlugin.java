package com.schwartzinc.sunday;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
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

    @Override
    public void load() {
        super.load();
        // Clear any stale syncing state on fresh process start
        writeSyncing(getContext(), false);
        dispatchIntentIfPresent(getActivity().getIntent());
    }

    @Override
    protected void handleOnNewIntent(Intent intent) {
        super.handleOnNewIntent(intent);
        dispatchIntentIfPresent(intent);
    }

    private static void writeSyncing(Context context, boolean syncing) {
        if (context == null) return;
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (syncing) {
            prefs.edit().putLong(KEY_SYNCING_SINCE, System.currentTimeMillis()).commit();
        } else {
            prefs.edit().remove(KEY_SYNCING_SINCE).commit();
        }
        SundayWidgetProvider.refreshAll(context);
    }

    private void dispatchIntentIfPresent(Intent intent) {
        if (intent == null) return;
        String action = intent.getStringExtra("widgetAction");
        if (action == null || action.isEmpty()) return;

        // When triggered by widget sync:
        // 1. Immediately send activity to background.
        // 2. If already syncing, drop the duplicate tap.
        // 3. Otherwise immediately mark syncing=true so the widget shows the spinner
        //    and disables clicks right away (even before JS finishes loading).
        if ("sync".equals(action)) {
            getActivity().runOnUiThread(() -> {
                getActivity().moveTaskToBack(true);
            });

            SharedPreferences prefs = getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            if (SundayWidgetProvider.isSyncing(prefs)) {
                // Drop duplicate sync request
                intent.removeExtra("widgetAction");
                intent.removeExtra("taskId");
                intent.removeExtra("columnId");
                return;
            }

            writeSyncing(getContext(), true);
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
        writeSyncing(getContext(), syncing);
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
