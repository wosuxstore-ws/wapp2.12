package com.wl.orders;

public class ApiConfig {

    // The folder on your hosting where the PHP files from php-api/ live.
    //
    // - Testing on the Android EMULATOR against a PHP server on your own
    //   computer (XAMPP/MAMP/`php -S 0.0.0.0:8000`): use 10.0.2.2, the
    //   emulator's alias for your computer's localhost:
    //       "http://10.0.2.2:8000/"
    //
    // - Real phone / live hosting: use the real domain, with https:
    //       "https://yourdomain.com/api/"
    public static final String BASE_URL = "https://wosux.com/api/";

    public static final String LOGIN_URL            = BASE_URL + "login.php";
    public static final String GET_ORDERS_URL       = BASE_URL + "get_orders.php";
    public static final String GET_ORDER_DETAIL_URL = BASE_URL + "get_order_detail.php";
    public static final String UPDATE_ORDER_STATUS_URL = BASE_URL + "update_order_status.php";
    public static final String GET_PRODUCTS_URL     = BASE_URL + "get_products.php";
    public static final String GET_CATEGORIES_URL   = BASE_URL + "get_categories.php";
    public static final String GET_NOTIFICATIONS_URL = BASE_URL + "get_notifications.php";
    public static final String MARK_NOTIFICATIONS_READ_URL = BASE_URL + "mark_notifications_read.php";
    public static final String POLL_NOTIFICATIONS_URL = BASE_URL + "poll_notifications.php";

    // Public web address of the folder with the product photos
    // (server folder: public_html/wl/backend/uploads/products). The products table only
    // stores the file name (front_image), so the app adds this in front of it.
    public static final String PRODUCT_IMAGE_BASE_URL = "https://wosux.com/wl/backend/uploads/products/";

    // Public web address of the folder with the category pictures
    // (server folder: public_html/wl/backend/uploads/categories). The categories table only
    // stores the file name (image_path), so the app adds this in front of it.
    public static final String CATEGORY_IMAGE_BASE_URL = "https://wosux.com/wl/backend/uploads/categories/";
}
