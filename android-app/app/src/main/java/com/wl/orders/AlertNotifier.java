package com.wl.orders;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.app.TaskStackBuilder;
import androidx.core.content.ContextCompat;

// Shows the status-bar alert (like a WhatsApp message). Tapping it opens the
// Notifications page, with Home underneath so Back goes to Home.
final class AlertNotifier {

    private static final String GROUP_KEY = "wl_orders_group";
    private static final int SUMMARY_ID = 1;       // id 2 is the listener's own notification
    static final int FIRST_ALERT_ID = 10;

    private AlertNotifier() {}

    @SuppressLint("MissingPermission") // checked just below
    static void show(Context context, long notificationId, String title, String body) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return; // the user said no to alerts
        }
        if (title == null || title.isEmpty()) title = "WL Orders";
        if (body == null) body = "";

        int id = FIRST_ALERT_ID + (int) (Math.abs(notificationId) % 1000000L);

        PushManager.ensureChannels(context);

        PendingIntent open = TaskStackBuilder.create(context)
                .addNextIntent(new Intent(context, HomeActivity.class))
                .addNextIntent(new Intent(context, NotificationsActivity.class))
                .getPendingIntent(id, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder alert = new NotificationCompat.Builder(context, PushManager.CHANNEL_ORDERS)
                .setSmallIcon(R.drawable.ic_stat_order)
                .setColor(ContextCompat.getColor(context, R.color.home_accent))
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(open)
                .setGroup(GROUP_KEY);

        // Several alerts stack into one group instead of filling the shade.
        NotificationCompat.Builder summary = new NotificationCompat.Builder(context, PushManager.CHANNEL_ORDERS)
                .setSmallIcon(R.drawable.ic_stat_order)
                .setColor(ContextCompat.getColor(context, R.color.home_accent))
                .setContentTitle("WL Orders")
                .setContentText("New notifications")
                .setStyle(new NotificationCompat.InboxStyle().setSummaryText("WL Orders"))
                .setGroup(GROUP_KEY)
                .setGroupSummary(true)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                .setAutoCancel(true)
                .setContentIntent(open);

        NotificationManagerCompat nm = NotificationManagerCompat.from(context);
        nm.notify(id, alert.build());
        nm.notify(SUMMARY_ID, summary.build());
    }
}
