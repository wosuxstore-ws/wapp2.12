package com.wl.orders;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.ImageLoader;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

// 3-column category grid inside the Categories popup on Home.
// The categories come from get_categories.php (the `categories` table): HomeActivity calls
// categoriesFromJson(...) and hands the list to this adapter. Each one is drawn as a circle
// with its picture (falls back to an icon when it has none). Picking one calls the
// OnCategorySelected callback, and HomeActivity then reloads the products for that category.
public class HomeCategoryAdapter extends RecyclerView.Adapter<HomeCategoryAdapter.ViewHolder> {

    public static class Category {
        // Value sent to get_products.php?category=... ("" = no filter = every product).
        final String name;
        final String label;     // text under the circle
        final String imageUrl;  // "" when the category has no picture
        @DrawableRes final int icon;

        public Category(String name, String label, String imageUrl, @DrawableRes int icon) {
            this.name = name;
            this.label = label;
            this.imageUrl = imageUrl;
            this.icon = icon;
        }
    }

    public interface OnCategorySelected {
        void onSelected(int position, Category category);
    }

    // First tile in the popup: clears the filter so Home shows every product again.
    public static Category all() {
        return new Category("", "All", "", R.drawable.ic_grid);
    }

    // get_categories.php "categories" array -> list for the popup ("All" comes first).
    public static List<Category> categoriesFromJson(JSONArray array) throws JSONException {
        List<Category> list = new ArrayList<>();
        list.add(all());
        for (int i = 0; i < array.length(); i++) {
            JSONObject c = array.getJSONObject(i);
            String name = Format.str(c, "name");
            if (name.isEmpty()) continue;
            list.add(new Category(
                    name,
                    name,
                    imageUrl(Format.str(c, "image_path")),
                    HomeProductAdapter.iconFor(name)));
        }
        return list;
    }

    // "7e84...webp" -> "https://wosux.com/wl/backend/uploads/categories/7e84...webp"
    static String imageUrl(String fileName) {
        if (fileName == null || fileName.trim().isEmpty()) return "";
        return ApiConfig.CATEGORY_IMAGE_BASE_URL + Uri.encode(fileName.trim());
    }

    private final List<Category> categories;
    private final OnCategorySelected listener;
    private int selected;

    // selectedName = the category that is currently active ("" = All).
    public HomeCategoryAdapter(List<Category> categories, String selectedName, OnCategorySelected listener) {
        this.categories = categories;
        this.listener = listener;
        this.selected = 0; // "All"
        for (int i = 0; i < categories.size(); i++) {
            if (categories.get(i).name.equalsIgnoreCase(selectedName)) {
                this.selected = i;
                break;
            }
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_category, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        Category c = categories.get(position);
        boolean isSelected = position == selected;

        h.icon.setImageResource(c.icon);
        h.label.setText(c.label);
        h.label.setTypeface(isSelected ? h.bold : h.regular);
        h.circle.setBackgroundResource(isSelected ? R.drawable.bg_category_selected : R.drawable.bg_category);
        h.ring.setVisibility(isSelected ? View.VISIBLE : View.GONE);
        h.check.setVisibility(isSelected ? View.VISIBLE : View.GONE);
        bindPhoto(h, c);

        h.itemView.setOnClickListener(v -> {
            int pos = h.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return;
            int previous = selected;
            selected = pos;
            if (previous != pos) {
                notifyItemChanged(previous);
                notifyItemChanged(pos);
            }
            // Fires even when the same category is tapped again, so the popup always closes.
            if (listener != null) listener.onSelected(pos, categories.get(pos));
        });
    }

    // Shows the category picture once it has loaded; until then (or if it fails, or the
    // category has none) the icon stays visible.
    private void bindPhoto(@NonNull ViewHolder h, Category c) {
        // This row may be re-used from another category: drop that category's pending picture.
        if (h.imageRequest != null) {
            h.imageRequest.cancelRequest();
            h.imageRequest = null;
        }
        h.photo.setImageDrawable(null);
        h.photo.setVisibility(View.GONE);
        h.icon.setVisibility(View.VISIBLE);
        if (c.imageUrl.isEmpty()) return;

        Context appContext = h.itemView.getContext().getApplicationContext();
        ImageLoader loader = VolleySingleton.getInstance(appContext).getImageLoader();
        h.imageRequest = loader.get(c.imageUrl, new ImageLoader.ImageListener() {
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
                // Keep the icon. Common causes: wrong CATEGORY_IMAGE_BASE_URL, file missing, 403.
            }
        }, 320, 320);
    }

    @Override
    public int getItemCount() {
        return categories.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final View circle;
        final ImageView icon;
        final ImageView photo;
        final View ring;
        final ImageView check;
        final TextView label;
        final Typeface regular;
        final Typeface bold;
        ImageLoader.ImageContainer imageRequest;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            circle = itemView.findViewById(R.id.categoryCircle);
            icon = itemView.findViewById(R.id.categoryIcon);
            photo = itemView.findViewById(R.id.categoryPhoto);
            ring = itemView.findViewById(R.id.categoryRing);
            check = itemView.findViewById(R.id.categoryCheck);
            label = itemView.findViewById(R.id.categoryLabel);
            regular = ResourcesCompat.getFont(itemView.getContext(), R.font.poppins_regular);
            bold = ResourcesCompat.getFont(itemView.getContext(), R.font.poppins_bold);
        }
    }
}
