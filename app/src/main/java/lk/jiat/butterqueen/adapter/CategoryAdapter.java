package lk.jiat.butterqueen.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bumptech.glide.Glide;

import java.util.List;

import lk.jiat.butterqueen.R;
import lk.jiat.butterqueen.data.CategoryImageCatalog;
import lk.jiat.butterqueen.model.Category;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {

    public interface OnCategoryClickListener {
        void onCategoryClick(Category category);
    }

    private final List<Category> categoryList;
    private final OnCategoryClickListener listener;

    public CategoryAdapter(List<Category> categoryList, OnCategoryClickListener listener) {
        this.categoryList = categoryList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category, parent, false);
        if (parent instanceof RecyclerView) {
            RecyclerView.LayoutManager manager = ((RecyclerView) parent).getLayoutManager();
            if (manager instanceof LinearLayoutManager
                    && ((LinearLayoutManager) manager).getOrientation()
                    == LinearLayoutManager.HORIZONTAL) {
                RecyclerView.LayoutParams params = (RecyclerView.LayoutParams)
                        view.getLayoutParams();
                params.width = (int) (168 * parent.getResources()
                        .getDisplayMetrics().density + 0.5f);
                view.setLayoutParams(params);
            }
        }
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        Category category = categoryList.get(position);

        holder.categoryNameTextView.setText(category.getName());
        holder.itemView.setContentDescription("Browse " + category.getName());

        // Respect administrator-uploaded Firestore images first.
        // Curated photography fills only otherwise-empty category cards.
        int fallback = CategoryImageCatalog.fallbackDrawable(category.getName());
        String imageUrl = category.getImageUrl();
        boolean hasCustomUrl = imageUrl != null && !imageUrl.trim().isEmpty();

        if (!hasCustomUrl && category.getImageResId() != 0) {
            // Cancel a prior Glide request when a recycled view displays a
            // legacy local drawable instead of a network image.
            Glide.with(holder.categoryImageView).clear(holder.categoryImageView);
            holder.categoryImageView.setImageResource(category.getImageResId());
        } else {
            if (!hasCustomUrl) {
                imageUrl = CategoryImageCatalog.recommendedUrl(category.getName());
            }

            if (imageUrl == null || imageUrl.trim().isEmpty()) {
                Glide.with(holder.categoryImageView).clear(holder.categoryImageView);
                holder.categoryImageView.setImageResource(fallback);
            } else {
                Glide.with(holder.categoryImageView)
                        .load(imageUrl)
                        .centerCrop()
                        .placeholder(fallback)
                        .error(fallback)
                        .into(holder.categoryImageView);
            }
        }
        holder.categoryImageView.setContentDescription(
                "Photo for " + (category.getName() == null ? "bakery" : category.getName()));

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCategoryClick(category);
            }
        });
    }

    @Override
    public int getItemCount() {
        return categoryList != null ? categoryList.size() : 0;
    }

    static class CategoryViewHolder extends RecyclerView.ViewHolder {

        ImageView categoryImageView;
        TextView categoryNameTextView;

        public CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            categoryImageView = itemView.findViewById(R.id.categoryImageView);
            categoryNameTextView = itemView.findViewById(R.id.categoryNameTextView);
        }
    }
}
