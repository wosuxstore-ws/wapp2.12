package com.wl.orders.models;

// One row of get_orders.php's "orders" array (items already flattened to text).
public class Order {
    public int id;
    public String orderNumber;
    public String status;
    public String grandTotal;
    public String recipientName;
    public String phone;
    public String email;        // only present if the orders table has an email column
    public String shippingMethod;
    public String paymentLabel;
    public String city;
    public String province;
    public String createdAt;
    public String itemsSummary; // e.g. "2 x Mouse\n1 x TV Box"
    public int itemCount;       // total quantity across all line items (used by the Home cards)
}
