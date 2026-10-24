package com.wl.orders;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

// Replaces Firebase. While someone is logged in, this service keeps one request open to
// php-api/poll_notifications.php on YOUR server. The server answers the moment a new order
// arrives (or after ~20 s with "nothing new"), and the phone asks again straight away.
// A new order therefore shows up within a few seconds, with nothing but your own hosting involved.
//
// Android requires a small always-on notification for a service like this ("Listening for new orders").
public class OrderAlertService extends Service {

    private static final int FOREGROUND_ID = 2;
    private static final int MAX_SEPARATE_ALERTS = 5;   // more than this at once -> one summary alert
    private static final long RETRY_DELAY_MS = 15_000;  // after a network / server error
    private static final long MIN_LOOP_MS = 3_000;      // never ask more often than this

    private static class SessionExpiredException extends Exception {}

    private volatile boolean running = false;
    private volatile HttpURLConnection current;
    private Thread worker;
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            startAsForeground();
        } catch (RuntimeException e) {
            // Android won't allow a foreground service right now (e.g. started from the background).
            stopSelf();
            return START_NOT_STICKY;
        }
        if (!new SessionManager(this).isLoggedIn()) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (worker == null || !worker.isAlive()) {
            running = true;
            worker = new Thread(this::loop, "wl-order-alerts");
            worker.start();
        }
        return START_STICKY;
    }

    private void startAsForeground() {
        PushManager.ensureChannels(this);
        PendingIntent open = PendingIntent.getActivity(this, 0,
                new Intent(this, HomeActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new NotificationCompat.Builder(this, PushManager.CHANNEL_SERVICE)
                .setSmallIcon(R.drawable.ic_stat_order)
                .setContentTitle("WL Orders")
                .setContentText("Listening for new orders")
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setContentIntent(open)
                .build();
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(FOREGROUND_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(FOREGROUND_ID, n);
        }
    }

    // ------------------------------------------------------------------ polling loop

    private void loop() {
        while (running) {
            SessionManager session = new SessionManager(this);
            if (!session.isLoggedIn()) break;

            long started = SystemClock.elapsedRealtime();
            try {
                poll(session.getToken());
            } catch (SessionExpiredException e) {
                break; // login expired: the app asks for a new login next time it is opened
            } catch (Exception e) {
                if (!running) break;
                sleepQuietly(RETRY_DELAY_MS);
                continue;
            }

            // The server normally holds the request for ~20 s. If it answered at once (a host that
            // doesn't allow long requests), wait a little so we don't hammer it.
            long took = SystemClock.elapsedRealtime() - started;
            if (took < MIN_LOOP_MS) sleepQuietly(MIN_LOOP_MS - took);
        }
        running = false;
        main.post(() -> {
            stopForeground(true);
            stopSelf();
        });
    }

    private void poll(String token) throws Exception {
        SharedPreferences prefs = PushManager.prefs(this);
        long last = prefs.getLong(PushManager.KEY_LAST_ID, -1);

        String url = ApiConfig.POLL_NOTIFICATIONS_URL + (last >= 0 ? "?after_id=" + last : "");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        current = c;
        try {
            c.setRequestMethod("GET");
            c.setConnectTimeout(10_000);
            c.setReadTimeout(45_000); // server holds ~20 s
            c.setUseCaches(false);
            c.setRequestProperty("X-Auth-Token", token);
            c.setRequestProperty("Accept", "application/json");

            int code;
            try {
                code = c.getResponseCode();
            } catch (IOException e) {
                // Some Android versions throw this instead of returning 401.
                String m = e.getMessage();
                if (m != null && m.toLowerCase().contains("authentication challenge")) {
                    throw new SessionExpiredException();
                }
                throw e;
            }
            if (code == 401) throw new SessionExpiredException();
            if (code != 200) throw new IOException("HTTP " + code);

            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
            }
            JSONObject json = new JSONObject(sb.toString());
            if (!json.optBoolean("success", false)) throw new IOException("Server said no");
            handle(prefs, last, json);
        } finally {
            current = null;
            c.disconnect();
        }
    }

    private void handle(SharedPreferences prefs, long last, JSONObject json) {
        if (!running) return;
        long latest = json.optLong("latest_id", last);

        // First run on this phone: remember where "now" is, don't alert for old orders.
        if (last < 0) {
            prefs.edit().putLong(PushManager.KEY_LAST_ID, latest).apply();
            return;
        }

        JSONArray list = json.optJSONArray("notifications");
        int count = list == null ? 0 : list.length();
        if (count > 0) {
            // Whatever screen is open (Home bell dot, Notifications list) refreshes itself.
            main.post(NotificationCenter::fireChanged);

            // Already looking at the list: no need for a status-bar alert too.
            if (!NotificationCenter.pageVisible) {
                if (count <= MAX_SEPARATE_ALERTS) {
                    for (int i = 0; i < count; i++) {
                        JSONObject n = list.optJSONObject(i);
                        if (n == null) continue;
                        AlertNotifier.show(this, n.optLong("id"), n.optString("title"), n.optString("body"));
                    }
                } else {
                    long newest = list.optJSONObject(count - 1).optLong("id");
                    AlertNotifier.show(this, newest, "WL Orders", count + " new notifications");
                }
            }
        }
        if (latest != last) {
            prefs.edit().putLong(PushManager.KEY_LAST_ID, latest).apply();
        }
    }

    private void sleepQuietly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            // service is being stopped
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        HttpURLConnection c = current;
        if (c != null) c.disconnect();      // aborts a request that is waiting for the server
        if (worker != null) worker.interrupt();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
