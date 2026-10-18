package com.schwartzinc.sunday;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

@CapacitorPlugin(name = "WidgetBridge")
public class WidgetBridgePlugin extends Plugin {
    public static final String PREFS_NAME = "sunday_widget";
    public static final String KEY_SNAPSHOT = "snapshot";

    @Override
    public void load() {
        super.load();
        dispatchIntentIfPresent(getActivity().getIntent());
    }

    @Override
    protected void handleOnNewIntent(Intent intent) {
        super.handleOnNewIntent(intent);
        dispatchIntentIfPresent(intent);
    }

    private void dispatchIntentIfPresent(Intent intent) {
        if (intent == null) return;
        String action = intent.getStringExtra("widgetAction");
        if (action == null || action.isEmpty()) return;

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
}
