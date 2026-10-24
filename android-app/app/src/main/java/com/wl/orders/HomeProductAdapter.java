package com.wl.orders;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ImageViewCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// 2-column product tiles on the Home screen. The tiles come from get_products.php, one page
// at a time: HomeActivity calls tilesFromJson(...) on each response, then setTiles(...) for
// the first page and addTiles(...) for the pages that follow while the user scrolls.
public class HomeProductAdapter extends RecyclerView.Adapter<HomeProductAdapter.ViewHolder> {

    public static class Tile {
        final int id;
        final String name;
        final String price;
        final String imageUrl; // "" when the product has no photo
        @DrawableRes final int icon;
        @ColorRes final int bg;
        @ColorRes final int fg;

        public Tile(int id, String name, String price, String imageUrl,
                    @DrawableRes int icon, @ColorRes int bg, @ColorRes int fg) {
            this.id = id;
            this.name = name;
            this.price = price;
            this.imageUrl = imageUrl;
            this.icon = icon;
            this.bg = bg;
            this.fg = fg;
        }
    }

    // Tile colors repeat in this order: blue, peach, green, lilac, blue, ...
    private static final @ColorRes int[][] TILE_COLORS = {
            {R.color.tile_blue_bg,  R.color.tile_blue_fg},
            {R.color.tile_peach_bg, R.color.tile_peach_fg},
            {R.color.tile_green_bg, R.color.tile_green_fg},
            {R.color.tile_lilac_bg, R.color.tile_lilac_fg},
    };

    // get_products.php "products" array -> tiles. startIndex = how many tiles are already
    // on screen, so the pastel colors keep rotating across pages.
    public static List<Tile> tilesFromJson(JSONArray array, int startIndex) throws JSONException {
        List<Tile> tiles = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject p = array.getJSONObject(i);
            int[] colors = TILE_COLORS[(startIndex + i) % TILE_COLORS.length];
            tiles.add(new Tile(
                    p.optInt("id"),
                    Format.str(p, "title"),
                    Format.moneyFixed(Format.str(p, "price")),
                    imageUrl(Format.str(p, "front_image")),
                    iconFor(Format.str(p, "category")),
                    colors[0],
                    colors[1]));
        }
        return tiles;
    }

    // "abc123.png" -> "https://wosux.com/wl/backend/uploads/products/abc123.png"
    static String imageUrl(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) return "";
        return ApiConfig.PRODUCT_IMAGE_BASE_URL + Uri.encode(fileName.trim());
    }

    // Picks one of the existing icons from the product's category name.
    @DrawableRes
    static int iconFor(String category) {
        String c = category == null ? "" : category.toLowerCase(Locale.US);
        if (c.contains("mouse"))                                          return R.drawable.ic_mouse;
        if (c.contains("earbud") || c.contains("handfree")
                || c.contains("handsfree"))                               return R.drawable.ic_handsfree;
        if (c.contains("headphone"))                                      return R.drawable.ic_headphones;
        if (c.contains("cable") || c.contains("usb"))                     return R.drawable.ic_cable;
        if (c.contains("connector"))                                      return R.drawable.ic_connector;
        if (c.contains("charger"))                                        return R.drawable.ic_plug;
        if (c.contains("power"))                                          return R.drawable.ic_powerbank;
        if (c.contains("speaker"))                                        return R.drawable.ic_speaker;
        if (c.contains("game"))                                           return R.drawable.ic_gamepad;
        if (c.contains("modem") || c.contains("network"))                 return R.drawable.ic_network;
        return R.drawable.ic_bag; // anything else
    }

    private List<Tile> tiles = new ArrayList<>();

    public void setTiles(List<Tile> newTiles) {
        this.tiles = new ArrayList<>(newTiles);
        notifyDataSetChanged();
    }

    // Adds the next page below the existing tiles (skips products that are already shown,
    // which can happen if a product is added while the user is scrolling).
    public void addTiles(List<Tile> more) {
        int start = tiles.size();
        List<Tile> fresh = new ArrayList<>();
        for (Tile t : more) {
            boolean known = false;
            for (Tile existing : tiles) {
                if (existing.id == t.id) { known = true; break; }
            }
            if (!known) fresh.add(t);
        }
        if (fresh.isEmpty()) return;
        tiles.addAll(fresh);
        notifyItemRangeInserted(start, fresh.size());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_home_product, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        Tile t = tiles.get(position);

        // Each tile needs its own background instance so colors don't bleed between tiles.
        // (Only visible behind the icon when a product has no photo.)
        GradientDrawable bg = (GradientDrawable) ContextCompat
                .getDrawable(h.itemView.getContext(), R.drawable.bg_tile).mutate();
        bg.setColor(ContextCompat.getColor(h.itemView.getContext(), t.bg));
        h.card.setBackground(bg);

        h.icon.setImageResource(t.icon);
        ImageViewCompat.setImageTintList(h.icon,
                ColorStateList.valueOf(ContextCompat.getColor(h.itemView.getContext(), t.fg)));
        h.name.setText(t.name);
        h.price.setText(t.price);
        bindPhoto(h, t);
    }

    // Shows the product photo once it has loaded; until then (or if it fails) the tinted
    // category icon stays visible.
    private void bindPhoto(@NonNull ViewHolder h, Tile t) {
        // This row may be re-used from another product: drop that product's pending photo.
        if (h.imageRequest != null) {
            h.imageRequest.cancelRequest();
            h.imageRequest = null;
        }
        h.photo.setImageDrawable(null);
        h.photo.setVisibility(View.GONE);
        h.icon.setVisibility(View.VISIBLE);
        if (t.imageUrl.isEmpty()) return;

        Context appContext = h.itemView.getContext().getApplicationContext();
        ImageLoader loader = VolleySingleton.getInstance(appContext).getImageLoader();
        h.imageRequest = loader.get(t.imageUrl, new ImageLoader.ImageListener() {
            @Override
            public void onResponse(ImageLoader.ImageContainer response, boolean isImmediate) {
                Bitmap bitmap = response.getBitmap();
                if (bitmap != null) { // null = the first "still loading" callback
                    h.photo.setImageBitmap(bitmap);
                    h.photo.setVisibility(View.VISIBLE);
                    h.icon.setVisibility(View.GONE);
                }
            }

            @Override
            public void onErrorResponse(VolleyError error) {
                // Keep the icon. Common causes: wrong PRODUCT_IMAGE_BASE_URL, file missing, 403.
            }
        }, 640, 640);
    }

    @Override
    public int getItemCount() {
        return tiles.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View card;
        ImageView icon;
        ImageView photo;
        TextView name;
        TextView price;
        ImageLoader.ImageContainer imageRequest;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            card = itemView.findViewById(R.id.tileCard);
            icon = itemView.findViewById(R.id.tileIcon);
            photo = itemView.findViewById(R.id.tilePhoto);
            name = itemView.findViewById(R.id.tileName);
            price = itemView.findViewById(R.id.tilePrice);
        }
    }
}
