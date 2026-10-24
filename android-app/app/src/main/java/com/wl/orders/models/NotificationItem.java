package com.wl.orders.models;

import com.wl.orders.Format;
import org.json.JSONObject;

// One row of the Notifications page (a row of the `notifications` table).
public class NotificationItem {
    public static final String TYPE_CHAT = "chat";

    public int id;
    public String type = "order";   // order | status | chat
    public String title = "";
    public String body = "";
    public int orderId;             // 0 = not about an order
    public long createdAtMillis;
    public boolean read;

    public boolean isChat() {
        return TYPE_CHAT.equals(type);
    }

    public static NotificationItem fromJson(JSONObject o) {
        NotificationItem n = new NotificationItem();
        n.id = o.optInt("id");
        n.type = Format.str(o, "type");
        n.title = Format.str(o, "title");
        n.body = Format.str(o, "body");
        n.orderId = o.optInt("order_id", 0);
        n.createdAtMillis = o.optLong("created_ts", 0L) * 1000L;
        n.read = o.optBoolean("is_read", false);
        return n;
    }
}
