package com.wl.orders;

import android.app.NotificationManager;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.wl.orders.models.NotificationItem;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

// The Notifications page (bell on Home, and what the new-order alert opens).
// Order alerts open that order; the double-tick button marks everything read.
public class NotificationsActivity extends AppCompatActivity {

    private static final long POLL_MS = 30_000; // quiet refresh while the page is open

    private SwipeRefreshLayout swipeRefresh;
    private TextView unreadText;
    private TextView emptyView;
    private View markAllButton;
    private NotificationAdapter adapter;
    private OrdersManager ordersManager;
    private SessionManager sessionManager;
    private int unread = 0;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            load(false);
            handler.postDelayed(this, POLL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        sessionManager = new SessionManager(this);
        if (!sessionManager.isLoggedIn()) {
            goToLogin();
            return;
        }

        swipeRefresh = findViewById(R.id.notificationsSwipe);
        swipeRefresh.setColorSchemeResources(R.color.home_blue);
        unreadText = findViewById(R.id.unreadText);
        emptyView = findViewById(R.id.notificationsEmpty);
        markAllButton = findViewById(R.id.markAllButton);
        RecyclerView recycler = findViewById(R.id.notificationsRecycler);

        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        markAllButton.setOnClickListener(v -> markAllRead());

        adapter = new NotificationAdapter(this::onNotificationClick);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        ordersManager = new OrdersManager(this, sessionManager.getToken());
        swipeRefresh.setOnRefreshListener(() -> load(true));
        emptyView.setOnClickListener(v -> load(true));

        load(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (ordersManager == null) return; // not logged in
        NotificationCenter.pageVisible = true;
        NotificationCenter.listener = () -> load(false); // a new notification arrived while we're open
        // Looking at the list: clear the status-bar alerts, like opening a chat in WhatsApp.
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.cancelAll();
        handler.postDelayed(poll, POLL_MS);
    }

    @Override
    protected void onPause() {
        super.onPause();
        NotificationCenter.pageVisible = false;
        NotificationCenter.listener = null;
        handler.removeCallbacks(poll);
    }

    private void load(boolean showSpinner) {
        if (showSpinner) swipeRefresh.setRefreshing(true);
        ordersManager.getNotifications(false, new ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                swipeRefresh.setRefreshing(false);
                try {
                    if (!response.getBoolean("success")) {
                        showProblem(response.optString("message", "Couldn't load notifications"));
                        return;
                    }
                    JSONArray array = response.getJSONArray("notifications");
                    List<NotificationItem> items = new ArrayList<>();
                    for (int i = 0; i < array.length(); i++) {
                        items.add(NotificationItem.fromJson(array.getJSONObject(i)));
                    }
                    adapter.setItems(items);
                    unread = response.optInt("unread", 0);
                    updateHeader();
                    emptyView.setText("No notifications yet");
                    emptyView.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
                } catch (JSONException e) {
                    showProblem("Unexpected response from server");
                }
            }

            @Override
            public void onError(String message) {
                swipeRefresh.setRefreshing(false);
                if (OrdersManager.SESSION_EXPIRED.equals(message)) {
                    sessionManager.clear();
                    Toast.makeText(NotificationsActivity.this,
                            "Session expired. Please log in again.", Toast.LENGTH_LONG).show();
                    goToLogin();
                    return;
                }
                showProblem("Couldn't reach server. Tap to retry.");
            }
        });
    }

    // Only replaces the list with a message when there is nothing on screen yet
    // (a failed quiet refresh shouldn't wipe notifications you're reading).
    private void showProblem(String text) {
        if (adapter.getNotificationCount() == 0) {
            emptyView.setText(text);
            emptyView.setVisibility(View.VISIBLE);
        }
    }

    private void onNotificationClick(NotificationItem item) {
        if (!item.read) {
            adapter.markRead(item.id);
            unread = Math.max(0, unread - 1);
            updateHeader();
            ordersManager.markNotificationRead(item.id, quietCallback());
        }
        if (item.orderId > 0) {
            Intent intent = new Intent(this, OrderDetailActivity.class);
            intent.putExtra("order_id", item.orderId);
            startActivity(intent);
        }
    }

    private void markAllRead() {
        if (unread == 0) return;
        adapter.markAllRead();
        unread = 0;
        updateHeader();
        ordersManager.markAllNotificationsRead(quietCallback());
    }

    private void updateHeader() {
        unreadText.setText(unread == 0 ? "All caught up" : unread + " unread");
        markAllButton.setAlpha(unread == 0 ? 0.4f : 1f);
    }

    // The screen already updated itself; if the server call fails, the next refresh corrects it.
    private ApiCallback quietCallback() {
        return new ApiCallback() {
            @Override public void onSuccess(JSONObject response) {}
            @Override public void onError(String message) {}
        };
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
