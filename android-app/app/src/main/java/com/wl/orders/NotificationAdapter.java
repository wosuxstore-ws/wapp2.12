package com.wl.orders;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.wl.orders.models.NotificationItem;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

// The Notifications list: rows grouped by day. Today has no heading (like the design);
// older days get a small grey heading ("YESTERDAY", "THU, 22 OCT").
// Unread rows have a blue dot and a bold title.
public class NotificationAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    public interface OnNotificationClick {
        void onClick(NotificationItem item);
    }

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_ROW = 1;

    // Each entry is either a String (day heading) or a NotificationItem.
    private final List<Object> rows = new ArrayList<>();
    private final OnNotificationClick listener;
    private Typeface boldFont;
    private Typeface mediumFont;

    public NotificationAdapter(OnNotificationClick listener) {
        this.listener = listener;
    }

    public void setItems(List<NotificationItem> items) {
        rows.clear();
        String lastLabel = null;
        for (NotificationItem item : items) {
            String label = dayLabel(item.createdAtMillis);
            if (!label.equals(lastLabel)) {
                if (!label.equals("TODAY")) rows.add(label);
                lastLabel = label;
            }
            rows.add(item);
        }
        notifyDataSetChanged();
    }

    public int getNotificationCount() {
        int n = 0;
        for (Object o : rows) if (o instanceof NotificationItem) n++;
        return n;
    }

    public void markRead(int notificationId) {
        for (int i = 0; i < rows.size(); i++) {
            Object o = rows.get(i);
            if (o instanceof NotificationItem && ((NotificationItem) o).id == notificationId) {
                ((NotificationItem) o).read = true;
                notifyItemChanged(i);
            }
        }
    }

    public void markAllRead() {
        for (Object o : rows) {
            if (o instanceof NotificationItem) ((NotificationItem) o).read = true;
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return rows.get(position) instanceof String ? TYPE_HEADER : TYPE_ROW;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (boldFont == null) {
            boldFont = ResourcesCompat.getFont(parent.getContext(), R.font.poppins_bold);
            mediumFont = ResourcesCompat.getFont(parent.getContext(), R.font.poppins_medium);
        }
        if (viewType == TYPE_HEADER) {
            return new HeaderHolder(inflater.inflate(R.layout.item_notification_header, parent, false));
        }
        return new RowHolder(inflater.inflate(R.layout.item_notification, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object row = rows.get(position);
        if (holder instanceof HeaderHolder) {
            ((HeaderHolder) holder).text.setText((String) row);
            return;
        }

        NotificationItem item = (NotificationItem) row;
        RowHolder h = (RowHolder) holder;
        h.title.setText(item.title);
        h.body.setText(item.body);
        h.body.setVisibility(item.body.isEmpty() ? View.GONE : View.VISIBLE);
        h.time.setText(new SimpleDateFormat("h:mm a", Locale.US).format(new Date(item.createdAtMillis)));

        h.dot.setVisibility(item.read ? View.INVISIBLE : View.VISIBLE);
        h.title.setTypeface(item.read ? mediumFont : boldFont);

        if (item.isChat()) {
            h.icon.setBackgroundResource(R.drawable.bg_icon_circle);   // light blue
            h.icon.setImageResource(R.drawable.ic_chat);
        } else {
            h.icon.setBackgroundResource(R.drawable.bg_circle_gray);   // light grey
            h.icon.setImageResource(R.drawable.ic_receipt);
        }

        h.itemView.setOnClickListener(v -> listener.onClick(item));
    }

    @Override
    public int getItemCount() {
        return rows.size();
    }

    // ------------------------------------------------------------ helpers

    private static String dayLabel(long millis) {
        Calendar then = Calendar.getInstance();
        then.setTimeInMillis(millis);
        Calendar now = Calendar.getInstance();
        if (sameDay(then, now)) return "TODAY";
        Calendar yesterday = (Calendar) now.clone();
        yesterday.add(Calendar.DAY_OF_YEAR, -1);
        if (sameDay(then, yesterday)) return "YESTERDAY";
        return new SimpleDateFormat("EEE, d MMM", Locale.US).format(new Date(millis)).toUpperCase(Locale.US);
    }

    private static boolean sameDay(Calendar a, Calendar b) {
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR)
                && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR);
    }

    static class HeaderHolder extends RecyclerView.ViewHolder {
        final TextView text;

        HeaderHolder(View v) {
            super(v);
            text = v.findViewById(R.id.notificationHeader);
        }
    }

    static class RowHolder extends RecyclerView.ViewHolder {
        final View dot;
        final ImageView icon;
        final TextView title, body, time;

        RowHolder(View v) {
            super(v);
            dot = v.findViewById(R.id.notificationDot);
            icon = v.findViewById(R.id.notificationIcon);
            title = v.findViewById(R.id.notificationTitle);
            body = v.findViewById(R.id.notificationBody);
            time = v.findViewById(R.id.notificationTime);
        }
    }
}
