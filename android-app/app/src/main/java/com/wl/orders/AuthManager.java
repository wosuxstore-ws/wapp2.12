package com.wl.orders;

import android.content.Context;
import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import org.json.JSONException;
import org.json.JSONObject;

public class AuthManager {

    private final Context context;

    public AuthManager(Context context) {
        this.context = context;
    }

    // Logs in against the `wapp` table (username + password).
    public void login(String username, String password, ApiCallback callback) {
        JSONObject params = new JSONObject();
        try {
            params.put("user", username);
            params.put("password", password);
        } catch (JSONException e) {
            callback.onError("Invalid input data");
            return;
        }

        JsonObjectRequest request = new JsonObjectRequest(
                Request.Method.POST,
                ApiConfig.LOGIN_URL,
                params,
                callback::onSuccess,
                error -> callback.onError(parseError(error))
        );
        request.setShouldCache(false);
        request.setRetryPolicy(new DefaultRetryPolicy(20000, 1, 1f));
        VolleySingleton.getInstance(context).addToRequestQueue(request);
    }

    private String parseError(VolleyError error) {
        if (error.networkResponse != null && error.networkResponse.data != null) {
            return new String(error.networkResponse.data);
        }
        return error.getMessage() != null ? error.getMessage() : "Network error";
    }
}
