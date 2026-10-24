package com.wl.orders;

import org.json.JSONObject;

// Generic callback used by AuthManager and OrdersManager so every
// Activity handles success/error the same simple way.
public interface ApiCallback {
    void onSuccess(JSONObject response);
    void onError(String message);
}
