package com.wl.orders;

import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.ColorRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

// Order details: hero card, status stepper, delivery details, items, totals and a
// button that moves the order to its next status.
public class OrderDetailActivity extends AppCompatActivity {

    private static final @ColorRes int[][] TILE_COLORS = {
            {R.color.tile_blue_bg,  R.color.tile_blue_fg},
            {R.color.tile_peach_bg, R.color.tile_peach_fg},
            {R.color.tile_green_bg, R.color.tile_green_fg},
            {R.color.tile_lilac_bg, R.color.tile_lilac_fg},
    };

    private TextView heroNumber, heroStatus, heroName, heroEmail, heroMeta, heroTotal;
    private TextView phoneView, addressView, nextButton;
    private LinearLayout stepper, itemsContainer, totalsContainer;
    private View detailScroll;
    private ProgressBar progressBar;
    private SessionManager sessionManager;
    private OrdersManager ordersManager;

    private int orderId;
    private String currentStatus = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_order_detail);

        heroNumber = findViewById(R.id.heroNumber);
        heroStatus = findViewById(R.id.heroStatus);
        heroName = findViewById(R.id.heroName);
        heroEmail = findViewById(R.id.heroEmail);
        heroMeta = findViewById(R.id.heroMeta);
        heroTotal = findViewById(R.id.heroTotal);
        phoneView = findViewById(R.id.detailPhone);
        addressView = findViewById(R.id.detailAddress);
        stepper = findViewById(R.id.stepper);
        itemsContainer = findViewById(R.id.itemsContainer);
        totalsContainer = findViewById(R.id.totalsContainer);
        detailScroll = findViewById(R.id.detailScroll);
        progressBar = findViewById(R.id.detailProgressBar);
        nextButton = findViewById(R.id.nextStatusButton);

        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        findViewById(R.id.moreButton).setOnClickListener(this::showMenu);
        nextButton.setOnClickListener(v -> moveToNextStatus());

        orderId = getIntent().getIntExtra("order_id", -1);
        sessionManager = new SessionManager(this);

        if (orderId <= 0 || !sessionManager.isLoggedIn()) {
            Toast.makeText(this, "Missing order", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        ordersManager = new OrdersManager(this, sessionManager.getToken());
        loadDetail();
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenu().add(0, 1, 0, "Refresh");
        menu.setOnMenuItemClickListener(item -> {
            loadDetail();
            return true;
        });
        menu.show();
    }

    private void loadDetail() {
        progressBar.setVisibility(View.VISIBLE);
        ordersManager.getOrderDetail(orderId, new ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                progressBar.setVisibility(View.GONE);
                try {
                    if (!response.getBoolean("success")) {
                        Toast.makeText(OrderDetailActivity.this, response.optString("message", "Order not found"), Toast.LENGTH_SHORT).show();
                        finish();
                        return;
                    }
                    show(response.getJSONObject("order"));
                    detailScroll.setVisibility(View.VISIBLE);
                } catch (JSONException e) {
                    Toast.makeText(OrderDetailActivity.this, "Unexpected response from server", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onError(String message) {
                progressBar.setVisibility(View.GONE);
                if (handleSessionExpired(message)) return;
                Toast.makeText(OrderDetailActivity.this, "Couldn't reach server: " + message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private boolean handleSessionExpired(String message) {
        if (!OrdersManager.SESSION_EXPIRED.equals(message)) return false;
        sessionManager.clear();
        Toast.makeText(this, "Session expired. Please log in again.", Toast.LENGTH_LONG).show();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
        return true;
    }

    // ---------------------------------------------------------------- bind

    private void show(JSONObject order) throws JSONException {
        currentStatus = Format.str(order, "status");

        // Hero card
        heroNumber.setText("#" + Format.str(order, "order_number"));
        heroName.setText(Format.str(order, "recipient_name"));
        heroTotal.setText(Format.moneyFixed(Format.str(order, "grand_total")));

        String email = Format.str(order, "email"); // only shown if the orders table has it
        heroEmail.setText(email);
        heroEmail.setVisibility(email.isEmpty() ? View.GONE : View.VISIBLE);

        JSONArray items = order.optJSONArray("items");
        int count = 0;
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                count += Math.max(1, items.getJSONObject(i).optInt("quantity", 1));
            }
        }
        heroMeta.setText((count == 1 ? "1 item" : count + " items")
                + " · " + Format.shortDate(Format.str(order, "created_at")));

        // Delivery details
        phoneView.setText(Format.str(order, "phone"));
        addressView.setText(buildAddress(order));

        // Items
        itemsContainer.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                itemsContainer.addView(buildItemRow(inflater, items.getJSONObject(i), i));
            }
        }

        // Totals
        totalsContainer.removeAllViews();
        addTotal(inflater, "Subtotal", Format.moneyFixed(Format.str(order, "retail_total")), false);
        addDiscount(inflater, "Product discount", order, "product_discount");
        addDiscount(inflater, "Coupon discount", order, "coupon_discount");
        addDiscount(inflater, "Points discount", order, "points_discount");
        addTotal(inflater, "Shipping", Format.moneyFixed(Format.str(order, "shipping_fee")), false);
        addTotal(inflater, "Total", Format.moneyFixed(Format.str(order, "grand_total")), true);
        addTotal(inflater, "Payment", Format.str(order, "payment_method_display_label"), false);

        applyStatus();
    }

    private String buildAddress(JSONObject order) {
        StringBuilder addr = new StringBuilder(Format.str(order, "address_line1"));
        String line2 = Format.str(order, "address_line2");
        if (!line2.isEmpty()) addr.append("\n").append(line2);

        StringBuilder cityLine = new StringBuilder(Format.str(order, "city"));
        appendPart(cityLine, ", ", Format.str(order, "province"));
        appendPart(cityLine, " ", Format.str(order, "postal_code"));
        if (cityLine.length() > 0) addr.append("\n").append(cityLine);

        String country = Format.str(order, "country");
        if (!country.isEmpty()) addr.append("\n").append(country);
        return addr.toString();
    }

    private void appendPart(StringBuilder sb, String separator, String part) {
        if (part.isEmpty()) return;
        if (sb.length() > 0) sb.append(separator);
        sb.append(part);
    }

    private View buildItemRow(LayoutInflater inflater, JSONObject item, int index) {
        View row = inflater.inflate(R.layout.item_detail_line, itemsContainer, false);
        String name = Format.str(item, "product_name");
        int qty = Math.max(1, item.optInt("quantity", 1));
        double unit = parseDouble(Format.str(item, "unit_price"));

        TextView icon = row.findViewById(R.id.lineIcon);
        int[] colors = TILE_COLORS[index % TILE_COLORS.length];
        icon.setText(OrderAdapter.initials(name).substring(0, 1));
        icon.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this, colors[0])));
        icon.setTextColor(ContextCompat.getColor(this, colors[1]));

        ((TextView) row.findViewById(R.id.lineName)).setText(name);
        ((TextView) row.findViewById(R.id.lineMeta)).setText(
                "Qty " + qty + " · " + Format.moneyFixed(String.valueOf(unit)) + " each");
        ((TextView) row.findViewById(R.id.lineTotal)).setText(Format.moneyFixed(String.valueOf(unit * qty)));
        return row;
    }

    private void addTotal(LayoutInflater inflater, String label, String value, boolean bold) {
        View row = inflater.inflate(R.layout.item_total_row, totalsContainer, false);
        TextView l = row.findViewById(R.id.totalLabel);
        TextView v = row.findViewById(R.id.totalValue);
        l.setText(label);
        v.setText(value);
        if (bold) {
            l.setTextColor(ContextCompat.getColor(this, R.color.home_title));
            l.setTextSize(15);
            l.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(this, R.font.poppins_bold));
            v.setTextSize(15);
            v.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(this, R.font.poppins_bold));
        }
        totalsContainer.addView(row);
    }

    private void addDiscount(LayoutInflater inflater, String label, JSONObject order, String key) {
        double amount = parseDouble(Format.str(order, key));
        if (amount > 0) {
            addTotal(inflater, label, "-" + Format.moneyFixed(String.valueOf(amount)), false);
        }
    }

    private double parseDouble(String raw) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ------------------------------------------------- status: pill, stepper, button

    private void applyStatus() {
        int step = OrderStatus.step(currentStatus);

        heroStatus.setText("● " + Format.statusLabel(currentStatus));

        // Stepper (hidden for cancelled orders)
        stepper.removeAllViews();
        stepper.setVisibility(step < 0 ? View.GONE : View.VISIBLE);
        if (step >= 0) {
            LayoutInflater inflater = LayoutInflater.from(this);
            int blue = ContextCompat.getColor(this, R.color.detail_button);
            int grey = ContextCompat.getColor(this, R.color.step_todo);
            for (int i = 0; i < OrderStatus.STEP_LABELS.length; i++) {
                View col = inflater.inflate(R.layout.item_step, stepper, false);
                View left = col.findViewById(R.id.stepLeft);
                View right = col.findViewById(R.id.stepRight);
                View dot = col.findViewById(R.id.stepDot);
                TextView label = col.findViewById(R.id.stepLabel);

                label.setText(OrderStatus.STEP_LABELS[i]);
                dot.setBackgroundResource(i < step ? R.drawable.bg_dot_done
                        : i == step ? R.drawable.bg_dot_current : R.drawable.bg_dot_todo);
                label.setTextColor(ContextCompat.getColor(this,
                        i <= step ? R.color.home_title : R.color.home_nav_inactive));

                // Line towards a step is blue once that step has been reached.
                left.setBackgroundColor(i <= step ? blue : grey);
                right.setBackgroundColor(i < step ? blue : grey);
                left.setVisibility(i == 0 ? View.INVISIBLE : View.VISIBLE);
                right.setVisibility(i == OrderStatus.STEP_LABELS.length - 1 ? View.INVISIBLE : View.VISIBLE);

                stepper.addView(col);
            }
        }

        // Bottom button
        String next = OrderStatus.next(currentStatus);
        if (next == null) {
            nextButton.setVisibility(View.GONE);
        } else {
            nextButton.setText(OrderStatus.nextButtonLabel(currentStatus));
            nextButton.setEnabled(true);
            nextButton.setVisibility(View.VISIBLE);
        }
    }

    private void moveToNextStatus() {
        String next = OrderStatus.next(currentStatus);
        if (next == null) return;

        nextButton.setEnabled(false);
        nextButton.setText("Updating…");
        ordersManager.updateStatus(orderId, next, new ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (response.optBoolean("success")) {
                    currentStatus = next;
                    OrdersManager.changeVersion++; // Home + Orders list reload when we go back
                    Toast.makeText(OrderDetailActivity.this,
                            "Order moved to " + Format.statusLabel(next), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(OrderDetailActivity.this,
                            response.optString("message", "Couldn't update order"), Toast.LENGTH_LONG).show();
                }
                applyStatus();
            }

            @Override
            public void onError(String message) {
                if (handleSessionExpired(message)) return;
                Toast.makeText(OrderDetailActivity.this, "Couldn't update order: " + message, Toast.LENGTH_LONG).show();
                applyStatus();
            }
        });
    }
}
