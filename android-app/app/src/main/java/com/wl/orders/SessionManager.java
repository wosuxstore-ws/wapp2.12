package com.wl.orders;

import android.content.Context;
import android.content.SharedPreferences;

// Remembers the logged-in admin (and their login token) on the phone.
// Good enough for a private admin app; for a public release, look at
// EncryptedSharedPreferences instead.
public class SessionManager {

    private static final String PREFS = "wl_admin_session";
    private static final String KEY_ADMIN_ID = "admin_id";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_TOKEN = "token";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void saveAdmin(int adminId, String username, String token) {
        prefs.edit()
                .putInt(KEY_ADMIN_ID, adminId)
                .putString(KEY_USERNAME, username)
                .putString(KEY_TOKEN, token)
                .apply();
    }

    public String getToken() {
        return prefs.getString(KEY_TOKEN, "");
    }

    public String getUsername() {
        return prefs.getString(KEY_USERNAME, "");
    }

    public boolean isLoggedIn() {
        return !getToken().isEmpty();
    }

    public void clear() {
        prefs.edit().clear().apply();
    }
}
