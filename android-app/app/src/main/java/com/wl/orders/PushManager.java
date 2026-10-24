package com.wl.orders;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import androidx.core.content.ContextCompat;

// Everything about getting THIS phone ready for new-order alerts, with no Firebase:
//  - the notification channels (Android 8+ needs them),
//  - starting / stopping OrderAlertService, which keeps asking YOUR server
//    (php-api/poll_notifications.php) whether something new arrived,
//  - asking once to be exempt from battery optimisation, so Android doesn't put the
//    listener to sleep while the screen is off.
public final class PushManager {

    public static final String CHANNEL_ORDERS = "new_orders";
    public static final String CHANNEL_SERVICE = "background_listener";

    static final String PREFS = "wl_push";
    static final String KEY_LAST_ID = "last_id";            // newest notification this phone has seen
    private static final String KEY_ASKED_BATTERY = "asked_battery";

    private PushManager() {}

    static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static void ensureChannels(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        if (nm == null) return;

        NotificationChannel orders = new NotificationChannel(
                CHANNEL_ORDERS, "New orders", NotificationManager.IMPORTANCE_HIGH);
        orders.setDescription("Alerts when a customer places a new order");
        orders.enableVibration(true);
        nm.createNotificationChannel(orders);

        // The small always-on notification Android requires while the listener runs.
        NotificationChannel service = new NotificationChannel(
                CHANNEL_SERVICE, "Background listener", NotificationManager.IMPORTANCE_MIN);
        service.setDescription("Shown while the app waits for new orders");
        service.setShowBadge(false);
        nm.createNotificationChannel(service);
    }

    // Call after login / on Home start. Safe to call as often as you like.
    public static void start(Context context) {
        Context app = context.getApplicationContext();
        if (!new SessionManager(app).isLoggedIn()) return;
        try {
            ContextCompat.startForegroundService(app, new Intent(app, OrderAlertService.class));
        } catch (RuntimeException ignored) {
            // Android refused to start it right now (app in background). It starts next time Home opens.
        }
    }

    // Call on log out (before or after sessionManager.clear()).
    public static void stop(Context context) {
        Context app = context.getApplicationContext();
        app.stopService(new Intent(app, OrderAlertService.class));
        prefs(app).edit().remove(KEY_LAST_ID).apply();
    }

    // One-time system dialog "Allow WL Orders to always run in the background?".
    // Without it, Android's Doze mode can pause the listener when the phone is idle.
    public static void askBatteryExemptionOnce(Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        SharedPreferences p = prefs(activity);
        if (p.getBoolean(KEY_ASKED_BATTERY, false)) return;
        PowerManager pm = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
        if (pm == null || pm.isIgnoringBatteryOptimizations(activity.getPackageName())) return;
        p.edit().putBoolean(KEY_ASKED_BATTERY, true).apply();
        try {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivity(i);
        } catch (RuntimeException ignored) {
            // This phone has no such screen: nothing to do.
        }
    }
}
