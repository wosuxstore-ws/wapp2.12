package com.wl.orders;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.wl.orders.models.Order;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// The Orders list. Holds every order plus the current status filter / search text.
public class OrderAdapter extends RecyclerView.Adapter<OrderAdapter.ViewHolder> {

    public interface OnOrderClick {
        void onClick(Order order);
    }

    // Avatar colours (same pastel pairs as the Home product tiles).
    private static final @ColorRes int[][] AVATAR_COLORS = {
            {R.color.tile_blue_bg,  R.color.tile_blue_fg},
            {R.color.tile_peach_bg, R.color.tile_peach_fg},
            {R.color.tile_green_bg, R.color.tile_green_fg},
            {R.color.tile_lilac_bg, R.color.tile_lilac_fg},
    };

    private List<Order> all = new ArrayList<>();
    private List<Order> shown = new ArrayList<>();
    private String filter = "all";
    private String query = "";
    private final OnOrderClick onOrderClick;

    public OrderAdapter(OnOrderClick onOrderClick) {
        this.onOrderClick = onOrderClick;
    }

    public void setOrders(List<Order> newOrders) {
        all = newOrders;
        apply();
    }

    public void setFilter(String key) {
        filter = key;
        apply();
    }

    public void setQuery(String text) {
        query = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        apply();
    }

    public int getAllCount() {
        return all.size();
    }

    private void apply() {
        List<Order> out = new ArrayList<>();
        for (Order o : all) {
            if (!filter.equals("all") && !OrderStatus.filterKey(o.status).equals(filter)) continue;
            if (!query.isEmpty() && !matches(o)) continue;
            out.add(o);
        }
        shown = out;
        notifyDataSetChanged();
    }

    private boolean matches(Order o) {
        return contains(o.orderNumber) || contains(o.recipientName) || contains(o.phone);
    }

    private boolean contains(String field) {
        return field != null && field.toLowerCase(Locale.ROOT).contains(query);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_order, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        Order order = shown.get(position);
        Context c = h.itemView.getContext();

        int[] colors = AVATAR_COLORS[Math.abs(String.valueOf(order.recipientName).hashCode()) % AVATAR_COLORS.length];
        h.avatar.setText(initials(order.recipientName));
        h.avatar.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(c, colors[0])));
        h.avatar.setTextColor(ContextCompat.getColor(c, colors[1]));

        h.customer.setText(order.recipientName);
        String items = order.itemCount == 1 ? "1 item" : order.itemCount + " items";
        h.meta.setText("#" + order.orderNumber + " · " + items);
        h.total.setText(Format.moneyFixed(order.grandTotal));

        h.status.setText(Format.statusLabel(order.status));
        h.status.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(c, OrderStatus.pillBg(order.status))));
        h.status.setTextColor(ContextCompat.getColor(c, OrderStatus.pillFg(order.status)));

        h.itemView.setOnClickListener(v -> onOrderClick.onClick(order));
    }

    @Override
    public int getItemCount() {
        return shown.size();
    }

    // "Amelia Hart" -> "AH", "Noah" -> "N"
    static String initials(String name) {
        if (name == null) return "?";
        String[] parts = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length && sb.length() < 2; i++) {
            if (!parts[i].isEmpty()) sb.append(Character.toUpperCase(parts[i].charAt(0)));
        }
        return sb.length() == 0 ? "?" : sb.toString();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView avatar, customer, meta, total, status;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.orderAvatar);
            customer = itemView.findViewById(R.id.orderCustomer);
            meta = itemView.findViewById(R.id.orderMeta);
            total = itemView.findViewById(R.id.orderTotal);
            status = itemView.findViewById(R.id.orderStatus);
        }
    }
}
