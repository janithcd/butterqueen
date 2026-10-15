package lk.jiat.butterqueen.adapter;

import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import lk.jiat.butterqueen.data.ProductRatingSummary;

import java.util.List;
import java.util.Locale;

import lk.jiat.butterqueen.R;
import lk.jiat.butterqueen.data.WishlistManager;
import lk.jiat.butterqueen.model.Product;
import lk.jiat.butterqueen.model.UnitPricing;

public class ProductAdapter extends RecyclerView.Adapter<ProductAdapter.ProductViewHolder> {

    public interface OnProductClickListener {
        void onProductClick(Product product);
    }

    public interface OnWishlistChangedListener {
        void onWishlistChanged(Product product, boolean isWishlisted, int position);
    }

    private final List<Product> productList;
    private final OnProductClickListener productClickListener;
    private final OnWishlistChangedListener wishlistChangedListener;
    // Cache avoids briefly flashing a zero rating on recycled cards while
    // Firestore attaches a new listener. Only visible cards observe Firestore.
    private final Map<String, ProductRatingSummary> ratingCache = new HashMap<>();
    private final Set<ProductViewHolder> activeHolders = new HashSet<>();

    public ProductAdapter(List<Product> productList,
                          OnProductClickListener productClickListener,
                          OnWishlistChangedListener wishlistChangedListener) {
        this.productList = productList;
        this.productClickListener = productClickListener;
        this.wishlistChangedListener = wishlistChangedListener;
    }

    @NonNull
    @Override
    public ProductViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_cake, parent, false);
        return new ProductViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProductViewHolder holder, int position) {
        Product product = productList.get(position);
        holder.stopRatingListener();
        holder.boundProductId = product.getId();
        holder.ratingSummaryTextView.setText(getCachedRatingLabel(holder.boundProductId));
        if (holder.attached) observeRating(holder);

        holder.productNameTextView.setText(product.getName());
        String description = product.getDescription() == null ? "" : product.getDescription();
        if (product.getCakeType() != null && !product.getCakeType().isEmpty()) {
            description = Character.toUpperCase(product.getCakeType().charAt(0))
                    + product.getCakeType().substring(1) + " cake • " + description;
        }
        holder.productDescriptionTextView.setText(description);
        holder.productPriceTextView.setText(
                !UnitPricing.isSupported(product.getUnitType())
                        ? "Price under review"
                        : !product.isAvailable() ? "Currently unavailable"
                        : UnitPricing.priceLabel(product.getPrice(),
                        UnitPricing.normalize(product.getUnitType(),
                                product.getCategoryName()))
        );

        if (product.getImageUrl() != null && !product.getImageUrl().trim().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(product.getImageUrl())
                    .placeholder(R.drawable.cake_wallpaper)
                    .error(R.drawable.cake_wallpaper)
                    .into(holder.productImageView);
        } else if (product.getImageResId() != 0) {
            holder.productImageView.setImageResource(product.getImageResId());
        } else {
            holder.productImageView.setImageResource(R.drawable.cake_wallpaper);
        }

        updateWishlistIcon(holder.wishlistButton, WishlistManager.isWishlisted(product.getId()));

        holder.itemView.setOnClickListener(v -> {
            if (productClickListener != null) {
                productClickListener.onProductClick(product);
            }
        });

        // Tap the heart, or double-tap the product image to update the wishlist.
        holder.wishlistButton.setOnClickListener(v -> toggleWishlistWithAnimation(holder, product));

        GestureDetector gestureDetector = new GestureDetector(
                holder.productImageView.getContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDown(@NonNull MotionEvent event) {
                        return true;
                    }

                    @Override
                    public boolean onSingleTapConfirmed(@NonNull MotionEvent event) {
                        if (productClickListener != null) productClickListener.onProductClick(product);
                        return true;
                    }

                    @Override
                    public boolean onDoubleTap(@NonNull MotionEvent event) {
                        toggleWishlistWithAnimation(holder, product);
                        return true;
                    }
                });
        holder.productImageView.setOnTouchListener((view, event) ->
                gestureDetector.onTouchEvent(event));
    }

    private String getCachedRatingLabel(String productId) {
        ProductRatingSummary summary = productId == null ? null : ratingCache.get(productId);
        return summary == null ? ProductRatingSummary.emptyLabel() : summary.label();
    }

    /**
     * One live listener per visible card. Listeners are removed when views are
     * detached/recycled, preventing excessive Firestore reads while scrolling.
     */
    private void observeRating(ProductViewHolder holder) {
        holder.stopRatingListener();
        final String productId = holder.boundProductId;
        if (!holder.attached || productId == null || productId.trim().isEmpty()) return;
        holder.ratingListener = FirebaseFirestore.getInstance()
                .collection("products").document(productId).collection("reviews")
                .addSnapshotListener((reviews, error) -> {
                    if (!holder.attached || !productId.equals(holder.boundProductId)) return;
                    if (error != null || reviews == null) {
                        holder.ratingSummaryTextView.setText(
                                ratingCache.containsKey(productId)
                                        ? getCachedRatingLabel(productId)
                                        : "★ Ratings unavailable");
                        return;
                    }
                    ProductRatingSummary summary = new ProductRatingSummary();
                    for (DocumentSnapshot review : reviews.getDocuments()) {
                        summary.add(review.getLong("stars"));
                    }
                    ratingCache.put(productId, summary);
                    holder.ratingSummaryTextView.setText(summary.label());
                });
    }

    @Override
    public void onViewAttachedToWindow(@NonNull ProductViewHolder holder) {
        super.onViewAttachedToWindow(holder);
        holder.attached = true;
        activeHolders.add(holder);
        observeRating(holder);
    }

    @Override
    public void onViewDetachedFromWindow(@NonNull ProductViewHolder holder) {
        holder.attached = false;
        holder.stopRatingListener();
        activeHolders.remove(holder);
        super.onViewDetachedFromWindow(holder);
    }

    @Override
    public void onViewRecycled(@NonNull ProductViewHolder holder) {
        holder.stopRatingListener();
        holder.boundProductId = null;
        super.onViewRecycled(holder);
    }

    @Override
    public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
        for (ProductViewHolder holder : new HashSet<>(activeHolders)) {
            holder.attached = false;
            holder.stopRatingListener();
        }
        activeHolders.clear();
        super.onDetachedFromRecyclerView(recyclerView);
    }

    private void toggleWishlistWithAnimation(ProductViewHolder holder, Product product) {
        boolean isWishlistedNow = WishlistManager.toggleWishlist(product);
        updateWishlistIcon(holder.wishlistButton, isWishlistedNow);

        // Reset first: RecyclerView reuses views when scrolling.
        holder.wishlistButton.animate().cancel();
        holder.wishlistButton.setScaleX(1f);
        holder.wishlistButton.setScaleY(1f);
        holder.wishlistButton.animate()
                .scaleX(1.28f).scaleY(1.28f)
                .setDuration(130)
                .withEndAction(() -> holder.wishlistButton.animate()
                        .scaleX(1f).scaleY(1f).setDuration(130).start())
                .start();

        Toast.makeText(holder.itemView.getContext(),
                isWishlistedNow ? "Added to wishlist" : "Removed from wishlist",
                Toast.LENGTH_SHORT).show();

        if (wishlistChangedListener != null) {
            int position = holder.getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                wishlistChangedListener.onWishlistChanged(product, isWishlistedNow, position);
            }
        }
    }

    private void updateWishlistIcon(ImageButton button, boolean isWishlisted) {
        int color = isWishlisted
                ? ContextCompat.getColor(button.getContext(), R.color.md_theme_primary)
                : ContextCompat.getColor(button.getContext(), R.color.md_theme_onSurface);

        button.setColorFilter(color);
    }

    @Override
    public int getItemCount() {
        return productList != null ? productList.size() : 0;
    }

    static class ProductViewHolder extends RecyclerView.ViewHolder {

        ImageView productImageView;
        TextView productNameTextView;
        TextView productDescriptionTextView;
        TextView productPriceTextView;
        TextView ratingSummaryTextView;
        ImageButton wishlistButton;
        String boundProductId;
        boolean attached;
        ListenerRegistration ratingListener;

        void stopRatingListener() {
            if (ratingListener != null) {
                ratingListener.remove();
                ratingListener = null;
            }
        }

        public ProductViewHolder(@NonNull View itemView) {
            super(itemView);
            productImageView = itemView.findViewById(R.id.cakeImageView);
            productNameTextView = itemView.findViewById(R.id.cakeNameTextView);
            productDescriptionTextView = itemView.findViewById(R.id.cakeDescriptionTextView);
            productPriceTextView = itemView.findViewById(R.id.cakePriceTextView);
            ratingSummaryTextView = itemView.findViewById(R.id.cakeRatingTextView);
            wishlistButton = itemView.findViewById(R.id.wishlistButton);
        }
    }
}
