package com.wl.orders;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.wl.orders.models.Order;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

// Dashboard. "Latest orders" (get_orders.php), "Products" (get_products.php) and the
// Categories popup (get_categories.php) are live; picking a category filters the Products
// section. Header, the Store Sales / Networking boxes and bottom nav are static for now.
public class HomeActivity extends AppCompatActivity {

    private static final int MAX_CARDS = 6;    // how many latest orders to show on Home
    private static final int PRODUCTS_PAGE = 12;       // products fetched per request
    private static final int LOAD_MORE_DISTANCE_DP = 600; // start the next page this far before the bottom

    private SwipeRefreshLayout swipeRefresh;
    private TextView ordersMessage;
    private RecyclerView ordersRecycler;
    private HomeOrderAdapter orderAdapter;
    private HomeProductAdapter productAdapter;
    private NestedScrollView homeScroll;
    private TextView productsMessage;
    private TextView productsFooter;

    // Paging state for the Products section (lazy loading)
    private int productsOffset = 0;          // ?offset= for the next request
    private boolean productsHasMore = false; // server said there is another page
    private boolean productsLoading = false;
    private boolean productsLoadFailed = false;
    private int productsGeneration = 0;      // bumped on every refresh so late replies are ignored
    private OrdersManager ordersManager;
    private SessionManager sessionManager;
    private int seenVersion;
    private View bellDot;
    // Android 13+ asks before an app may show alerts. Whatever the answer, we still start the
    // order listener (alerts simply won't show if the user said no).
    private final ActivityResultLauncher<String> notificationPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(),
                    granted -> startOrderAlerts());
    private TextView productsTitle;
    // Categories popup: list from get_categories.php ("All" first). selectedCategory is the
    // category NAME used as ?category= filter; "" = All (no filter).
    private List<HomeCategoryAdapter.Category> categories = new ArrayList<>();
    private String selectedCategory = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        sessionManager = new SessionManager(this);
        if (!sessionManager.isLoggedIn()) {
            goToLogin();
            return;
        }

        swipeRefresh = findViewById(R.id.homeSwipeRefresh);
        swipeRefresh.setColorSchemeResources(R.color.home_blue);
        ordersMessage = findViewById(R.id.ordersMessage);
        ordersRecycler = findViewById(R.id.ordersRecycler);

        // Latest orders (dynamic)
        orderAdapter = new HomeOrderAdapter(order -> {
            Intent intent = new Intent(this, OrderDetailActivity.class);
            intent.putExtra("order_id", order.id);
            startActivity(intent);
        });
        ordersRecycler.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        ordersRecycler.setAdapter(orderAdapter);

        // Products (dynamic, from get_products.php)
        RecyclerView productsRecycler = findViewById(R.id.productsRecycler);
        productsMessage = findViewById(R.id.productsMessage);
        productsFooter = findViewById(R.id.productsFooter);
        productsTitle = findViewById(R.id.productsTitle);
        homeScroll = findViewById(R.id.homeScroll);
        productAdapter = new HomeProductAdapter();
        productsRecycler.setLayoutManager(new GridLayoutManager(this, 2));
        productsRecycler.setAdapter(productAdapter);

        // Categories icon next to "Products" -> bottom-sheet popup
        findViewById(R.id.categoriesButton).setOnClickListener(v -> showCategories());

        // Quick-access boxes. "Orders" opens the full orders list. Store Sales and Networking
        // have no screens yet: replace comingSoon(...) with
        // startActivity(new Intent(this, <YourActivity>.class)) once each one exists.
        findViewById(R.id.boxStoreSales).setOnClickListener(v -> comingSoon("Store Sales"));
        findViewById(R.id.boxNetworking).setOnClickListener(v -> comingSoon("Networking"));
        findViewById(R.id.boxOrders).setOnClickListener(v ->
                startActivity(new Intent(this, OrdersActivity.class)));

        // Top-left grid icon: small menu so Refresh / Log out stay reachable from Home
        findViewById(R.id.menuButton).setOnClickListener(this::showMenu);

        // Tap the empty/error messages to retry
        ordersMessage.setOnClickListener(v -> loadOrders());
        productsMessage.setOnClickListener(v -> loadProducts());
        productsFooter.setOnClickListener(v -> {
            if (productsLoadFailed) loadMoreProducts(); // "Couldn't load more. Tap to retry."
        });

        // Lazy loading: when the user scrolls down close to the end of the grid, fetch the next page.
        homeScroll.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener)
                (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
                    if (scrollY > oldScrollY) maybeLoadMoreProducts();
                });

        ordersManager = new OrdersManager(this, sessionManager.getToken());
        swipeRefresh.setOnRefreshListener(this::refreshAll);

        // Bell -> Notifications page. The red dot only shows while something is unread.
        bellDot = findViewById(R.id.bellDot);
        findViewById(R.id.bellButton).setOnClickListener(v ->
                startActivity(new Intent(this, NotificationsActivity.class)));

        setUpPushAlerts();
        refreshAll();
    }

    // New-order alerts: create the channels, ask for permission (Android 13+), then start the
    // listener that asks your own server for new orders (no Firebase).
    private void setUpPushAlerts() {
        PushManager.ensureChannels(this);
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS);
        } else {
            startOrderAlerts();
        }
    }

    private void startOrderAlerts() {
        PushManager.start(this);
        PushManager.askBatteryExemptionOnce(this); // one-time "always run in background?" dialog
    }

    // Shows / hides the red dot on the bell.
    private void refreshBell() {
        ordersManager.getNotifications(true, new ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                bellDot.setVisibility(response.optInt("unread", 0) > 0 ? View.VISIBLE : View.GONE);
            }

            @Override
            public void onError(String message) {
                // keep the dot as it was
            }
        });
    }

    private void refreshAll() {
        loadOrders();
        loadCategories();
        loadProducts();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // An order's status was changed on the details screen -> reload the cards.
        if (ordersManager != null && seenVersion != OrdersManager.changeVersion) {
            loadOrders();
        }
        if (ordersManager != null) {
            refreshBell();
            // A new order arrived while Home is open: update the bell dot and show the new order.
            NotificationCenter.listener = () -> {
                refreshBell();
                loadOrders();
            };
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        NotificationCenter.listener = null;
    }

    private void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(this, anchor);
        menu.getMenuInflater().inflate(R.menu.orders_menu, menu.getMenu()); // Refresh + Log out
        menu.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_refresh) {
                refreshAll();
                return true;
            }
            if (id == R.id.action_logout) {
                PushManager.stop(this); // stop alerts on this phone
                sessionManager.clear();
                goToLogin();
                return true;
            }
            return false;
        });
        menu.show();
    }

    // Categories popup: a bottom sheet with a 3-column grid of round category pictures
    // (from the categories table). Picking one closes the popup and reloads the Products
    // section with only that category's products; "All" shows every product again.
    private void showCategories() {
        if (categories.isEmpty()) {
            // Not loaded yet (or the request failed): try again instead of showing an empty popup.
            Toast.makeText(this, "Loading categories…", Toast.LENGTH_SHORT).show();
            loadCategories();
            return;
        }

        BottomSheetDialog dialog = new BottomSheetDialog(this, R.style.ThemeOverlay_WLOrders_BottomSheetDialog);
        View sheet = getLayoutInflater().inflate(R.layout.sheet_categories, null);

        RecyclerView grid = sheet.findViewById(R.id.categoriesRecycler);
        grid.setLayoutManager(new GridLayoutManager(this, 3));
        grid.setAdapter(new HomeCategoryAdapter(categories, selectedCategory,
                (position, category) -> {
                    dialog.dismiss();
                    selectCategory(category.name);
                }));

        sheet.findViewById(R.id.categoriesClose).setOnClickListener(v -> dialog.dismiss());

        dialog.setContentView(sheet);
        // Open fully expanded so every category is visible straight away.
        dialog.setOnShowListener(d -> {
            View container = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (container != null) {
                BottomSheetBehavior<View> behavior = BottomSheetBehavior.from(container);
                behavior.setSkipCollapsed(true);
                behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            }
        });
        dialog.show();
    }

    // Applies a category filter ("" = All) and reloads the Products section.
    private void selectCategory(String name) {
        if (name.equalsIgnoreCase(selectedCategory)) return; // same one again: nothing to reload
        selectedCategory = name;
        productsTitle.setText(name.isEmpty() ? "Products" : name);

        // Clear the old grid right away so the previous category's products don't linger.
        productAdapter.setTiles(new ArrayList<>());
        productsMessage.setText("Loading…");
        productsMessage.setVisibility(View.VISIBLE);
        loadProducts();
    }

    // Fills the popup from get_categories.php. Quiet on failure: the popup retries when opened.
    private void loadCategories() {
        ordersManager.getCategories(new ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                try {
                    if (!response.getBoolean("success")) return;
                    categories = HomeCategoryAdapter.categoriesFromJson(response.getJSONArray("categories"));
                } catch (JSONException e) {
                    // keep whatever list we had
                }
            }

            @Override
            public void onError(String message) {
                // keep whatever list we had
            }
        });
    }

    private void comingSoon(String name) {
        Toast.makeText(this, name + " is coming soon", Toast.LENGTH_SHORT).show();
    }

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void loadOrders() {
        seenVersion = OrdersManager.changeVersion;
        swipeRefresh.setRefreshing(true);
        ordersManager.getOrders(new ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                swipeRefresh.setRefreshing(false);
                try {
                    if (!response.getBoolean("success")) {
                        showMessage(response.optString("message", "Couldn't load orders"));
                        return;
                    }
                    JSONArray array = response.getJSONArray("orders"); // already newest first
                    List<Order> latest = new ArrayList<>();
                    for (int i = 0; i < array.length() && i < MAX_CARDS; i++) {
                        latest.add(parseOrder(array.getJSONObject(i)));
                    }
                    if (latest.isEmpty()) {
                        showMessage("No orders yet");
                    } else {
                        orderAdapter.setOrders(latest);
                        ordersMessage.setVisibility(View.GONE);
                        ordersRecycler.setVisibility(View.VISIBLE);
                        ordersRecycler.scrollToPosition(0);
                    }
                } catch (JSONException e) {
                    showMessage("Unexpected response from server");
                }
            }

            @Override
            public void onError(String message) {
                swipeRefresh.setRefreshing(false);
                if (OrdersManager.SESSION_EXPIRED.equals(message)) {
                    sessionManager.clear();
                    Toast.makeText(HomeActivity.this, "Session expired. Please log in again.", Toast.LENGTH_LONG).show();
                    goToLogin();
                    return;
                }
                showMessage("Couldn't reach server. Tap to retry.");
            }
        });
    }

    // ------------------------------------------------------------ products (paged)

    // Starts over from the first page (app start, pull-to-refresh, menu Refresh, tap to retry).
    private void loadProducts() {
        productsGeneration++;
        productsOffset = 0;
        productsHasMore = false;
        productsLoading = false;
        productsLoadFailed = false;
        productsFooter.setVisibility(View.GONE);
        fetchProductsPage(true);
    }

    private void loadMoreProducts() {
        if (productsLoading || !productsHasMore) return;
        productsLoadFailed = false;
        fetchProductsPage(false);
    }

    // Called while scrolling (and after a page arrives): loads the next page once the
    // user is within LOAD_MORE_DISTANCE_DP of the bottom.
    private void maybeLoadMoreProducts() {
        if (productsLoading || !productsHasMore || productsLoadFailed) return;
        View content = homeScroll.getChildAt(0);
        if (content == null) return;
        int distanceToBottom = content.getBottom() - (homeScroll.getHeight() + homeScroll.getScrollY());
        int threshold = (int) (LOAD_MORE_DISTANCE_DP * getResources().getDisplayMetrics().density);
        if (distanceToBottom <= threshold) loadMoreProducts();
    }

    private void fetchProductsPage(boolean firstPage) {
        final int generation = productsGeneration;
        productsLoading = true;
        if (!firstPage) {
            productsFooter.setText("Loading more…");
            productsFooter.setVisibility(View.VISIBLE);
        }

        ordersManager.getProducts(PRODUCTS_PAGE, productsOffset, selectedCategory, new ApiCallback() {
            @Override
            public void onSuccess(JSONObject response) {
                if (generation != productsGeneration) return; // a newer refresh replaced this request
                productsLoading = false;
                try {
                    if (!response.getBoolean("success")) {
                        productsFailed(firstPage, response.optString("message", "Couldn't load products"));
                        return;
                    }
                    JSONArray array = response.getJSONArray("products");
                    List<HomeProductAdapter.Tile> tiles = HomeProductAdapter.tilesFromJson(
                            array, firstPage ? 0 : productAdapter.getItemCount());
                    if (firstPage) {
                        productAdapter.setTiles(tiles);
                    } else {
                        productAdapter.addTiles(tiles);
                    }

                    productsOffset = response.optInt("next_offset", productsOffset + array.length());
                    productsHasMore = response.optBoolean("has_more", false);
                    productsFooter.setVisibility(View.GONE);

                    if (productAdapter.getItemCount() == 0) {
                        productsMessage.setText(selectedCategory.isEmpty()
                                ? "No products yet"
                                : "No products in " + selectedCategory);
                        productsMessage.setVisibility(View.VISIBLE);
                    } else {
                        productsMessage.setVisibility(View.GONE);
                        // The screen may still not be full (or the user already scrolled to the end).
                        homeScroll.post(HomeActivity.this::maybeLoadMoreProducts);
                    }
                } catch (JSONException e) {
                    productsFailed(firstPage, "Unexpected response from server");
                }
            }

            @Override
            public void onError(String message) {
                if (generation != productsGeneration) return;
                productsLoading = false;
                if (OrdersManager.SESSION_EXPIRED.equals(message)) {
                    return; // loadOrders() already sends the user back to the login screen
                }
                productsFailed(firstPage, "Couldn't load products. Tap to retry.");
            }
        });
    }

    private void productsFailed(boolean firstPage, String text) {
        if (firstPage) {
            if (productAdapter.getItemCount() == 0) {
                productsMessage.setText(text);
                productsMessage.setVisibility(View.VISIBLE);
            } else {
                Toast.makeText(this, "Couldn't refresh products", Toast.LENGTH_SHORT).show();
            }
        } else {
            productsLoadFailed = true; // stop auto-loading until the user taps the footer
            productsFooter.setText("Couldn't load more. Tap to retry.");
            productsFooter.setVisibility(View.VISIBLE);
        }
    }

    private void showMessage(String text) {
        ordersRecycler.setVisibility(View.INVISIBLE);
        ordersMessage.setText(text);
        ordersMessage.setVisibility(View.VISIBLE);
    }

    private Order parseOrder(JSONObject o) throws JSONException {
        Order order = new Order();
        order.id = o.getInt("id");
        order.orderNumber = Format.str(o, "order_number");
        order.status = Format.str(o, "status");
        order.grandTotal = Format.str(o, "grand_total");
        order.recipientName = Format.str(o, "recipient_name");
        order.createdAt = Format.str(o, "created_at");
        order.email = Format.str(o, "email");

        int count = 0;
        JSONArray items = o.optJSONArray("items");
        if (items != null) {
            for (int i = 0; i < items.length(); i++) {
                count += Math.max(1, items.getJSONObject(i).optInt("quantity", 1));
            }
        }
        order.itemCount = count;
        return order;
    }
}
