package com.wl.orders;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.wl.orders.models.Order;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

// The "Orders" box on Home lands here: every order from every customer as a searchable,
// filterable list. Tapping a row opens OrderDetailActivity.
public class OrdersActivity extends AppCompatActivity {

    private SwipeRefreshLayout swipeRefresh;
    private TextView emptyView;
    private LinearLayout chipsContainer;
    private OrdersManager ordersManager;
    private SessionManager sessionManager;
    private OrderAdapter adapter;
    private int seenVersion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_orders);

        sessionManager = new SessionManager(this);
        if (!sessionManager.isLoggedIn()) {
            goToLogin();
            return;
        }

        swipeRefresh = findViewById(R.id.swipeRefresh);
        swipeRefresh.setColorSchemeResources(R.color.home_blue);
        emptyView = findViewById(R.id.emptyView);
        chipsContainer = findViewById(R.id.chipsContainer);
        RecyclerView recyclerView = findViewById(R.id.ordersRecyclerView);

        // Header + bottom nav
        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        findViewById(R.id.moreButton).setOnClickListener(this::showMenu);
        findViewById(R.id.navHome).setOnClickListener(v -> finish()); // Home is the screen below us

        // List
        adapter = new OrderAdapter(order -> {
            Intent intent = new Intent(this, OrderDetailActivity.class);
            intent.putExtra("order_id", order.id);
            startActivity(intent);
        });
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);

        // Search
        EditText search = findViewById(R.id.searchInput);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                adapter.setQuery(s.toString());
                updateEmptyView();
            }
        });

        // Status chips
        for (int i = 0; i < chipsContainer.getChildCount(); i++) {
            View chip = chipsContainer.getChildAt(i);
            chip.setOnClickListener(v -> selectChip(v));
        }

        ordersManager = new OrdersManager(this, sessionManager.getToken());
        swipeRefresh.setOnRefreshListener(this::loadOrders);

        loadOrders();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // An order's status was changed on the details screen -> reload.
        if (ordersManager != null && seenVersion != OrdersManager.changeVersion) {
            loadOrders();
        }
    }

    private void selectChip(View selected) {
        for (int i = 0; i < chipsContainer.getChildCount(); i++) {
            View chip = chipsContainer.getChildAt(i);
            chip.setBackgroundResource(chip == selected ? R.drawable.bg_chip_selected : R.drawable.bg_chip);
        }
        adapter.setFilter((String) selected.getTag());
        updateEmptyView();
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenuInflater().inflate(R.menu.orders_menu, menu.getMenu()); // Refresh + Log out
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_refresh) {
                loadOrders();
                return true;
            }
            if (id == R.id.action_logout) {
                PushManager.stop(this); // stop alerts on this phone
                sessionManager.clear();
                goToLogin();
                return true;
            }
            return false;
        });
        menu.show();
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void updateEmptyView() {
        boolean noOrders = adapter.getAllCount() == 0;
        boolean noMatches = adapter.getItemCount() == 0;
        emptyView.setText(noOrders ? "No orders yet" : "No matching orders");
        emptyView.setVisibility(noMatches ? View.VISIBLE : View.GONE);
    }

    private void loadOrders() {
        seenVersion = OrdersManager.changeVersion;
        swipeRefresh.setRefreshing(true);
        ordersManager.getOrders(new ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                swipeRefresh.setRefreshing(false);
                try {
                    if (response.getBoolean("success")) {
                        JSONArray array = response.getJSONArray("orders");
                        List<Order> orders = new ArrayList<>();
                        for (int i = 0; i < array.length(); i++) {
                            orders.add(parseOrder(array.getJSONObject(i)));
                        }
                        adapter.setOrders(orders);
                        updateEmptyView();
                    } else {
                        Toast.makeText(OrdersActivity.this, response.optString("message", "Couldn't load orders"), Toast.LENGTH_SHORT).show();
                    }
                } catch (JSONException e) {
                    Toast.makeText(OrdersActivity.this, "Unexpected response from server", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String message) {
                swipeRefresh.setRefreshing(false);
                if (OrdersManager.SESSION_EXPIRED.equals(message)) {
                    sessionManager.clear();
                    Toast.makeText(OrdersActivity.this, "Session expired. Please log in again.", Toast.LENGTH_LONG).show();
                    goToLogin();
                    return;
                }
                Toast.makeText(OrdersActivity.this, "Couldn't reach server: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private Order parseOrder(JSONObject o) throws JSONException {
        Order order = new Order();
        order.id = o.getInt("id");
        order.orderNumber = Format.str(o, "order_number");
        order.status = Format.str(o, "status");
        order.grandTotal = Format.str(o, "grand_total");
        order.recipientName = Format.str(o, "recipient_name");
        order.phone = Format.str(o, "phone");
        order.createdAt = Format.str(o, "created_at");

        int count = 0;
        JSONArray items = o.optJSONArray("items");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                count += Math.max(1, items.getJSONObject(i).optInt("quantity", 1));
            }
        }
        order.itemCount = count;
        return order;
    }
}
