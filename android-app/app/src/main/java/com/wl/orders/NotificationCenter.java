package com.wl.orders;

// Tiny bridge between the order listener (OrderAlertService) and whichever screen is open:
// when something new arrives, the open screen gets told to refresh itself.
final class NotificationCenter {

    private NotificationCenter() {}

    // True while the Notifications page is on screen (then no status-bar alert is needed).
    static volatile boolean pageVisible = false;

    // Set by Home / Notifications while they are on screen. Always called on the main thread.
    static volatile Runnable listener = null;

    static void fireChanged() {
        Runnable l = listener;
        if (l != null) l.run();
    }
}
