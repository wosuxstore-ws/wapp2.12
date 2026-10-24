package com.wl.orders;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.wl.orders.models.Order;
import java.util.ArrayList;
import java.util.List;

// Horizontal "Latest orders" cards on the Home screen (light-blue gradient cards).
public class HomeOrderAdapter extends RecyclerView.Adapter<HomeOrderAdapter.ViewHolder> {

    public interface OnOrderClick {
        void onClick(Order order);
    }

    private static final float CARD_PEEK_DP = 90f; // space left for the next card to peek in

    private List<Order> orders = new ArrayList<>();
    private final OnOrderClick onOrderClick;

    public HomeOrderAdapter(OnOrderClick onOrderClick) {
        this.onOrderClick = onOrderClick;
    }

    public void setOrders(List<Order> newOrders) {
        this.orders = newOrders;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_home_order, parent, false);
        // Card = screen width minus a peek of the next card (300dp on a 390dp phone, like the design).
        float density = parent.getResources().getDisplayMetrics().density;
        int screenWidth = parent.getResources().getDisplayMetrics().widthPixels;
        view.getLayoutParams().width = screenWidth - Math.round(CARD_PEEK_DP * density);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        Order order = orders.get(position);

        h.number.setText("#" + order.orderNumber);
        h.status.setText("● " + Format.statusLabel(order.status));
        h.customer.setText(order.recipientName);
        String email = order.email == null ? "" : order.email;
        h.email.setText(email);
        h.email.setVisibility(email.isEmpty() ? View.GONE : View.VISIBLE);
        String items = order.itemCount == 1 ? "1 item" : order.itemCount + " items";
        h.meta.setText(items + " · " + Format.shortDate(order.createdAt));
        h.total.setText(Format.moneyFixed(order.grandTotal));

        h.itemView.setOnClickListener(v -> onOrderClick.onClick(order));
    }

    @Override
    public int getItemCount() {
        return orders.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView number, status, customer, email, meta, total;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            number = itemView.findViewById(R.id.cardOrderNumber);
            status = itemView.findViewById(R.id.cardStatus);
            customer = itemView.findViewById(R.id.cardCustomer);
            email = itemView.findViewById(R.id.cardEmail);
            meta = itemView.findViewById(R.id.cardMeta);
            total = itemView.findViewById(R.id.cardTotal);
        }
    }
}
