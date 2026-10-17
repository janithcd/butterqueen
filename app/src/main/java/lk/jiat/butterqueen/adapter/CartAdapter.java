package lk.jiat.butterqueen.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;
import java.util.Locale;

import lk.jiat.butterqueen.R;
import lk.jiat.butterqueen.model.CartItem;
import lk.jiat.butterqueen.model.UnitPricing;

public class CartAdapter extends RecyclerView.Adapter<CartAdapter.CartViewHolder> {

    public interface OnCartActionListener {
        void onIncrease(int position);
        void onDecrease(int position);
        void onRemove(int position);
    }

    private final List<CartItem> cartItems;
    private final OnCartActionListener listener;

    public CartAdapter(List<CartItem> cartItems, OnCartActionListener listener) {
        this.cartItems = cartItems;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CartViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_cart, parent, false);
        return new CartViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CartViewHolder holder, int position) {
        CartItem cartItem = cartItems.get(position);

        holder.productNameTextView.setText(cartItem.getProductName());
        holder.productPriceTextView.setText(
                UnitPricing.priceLabel(cartItem.getProductPrice(), cartItem.getUnitType())
        );

        if (cartItem.getProductImageUrl() != null && !cartItem.getProductImageUrl().trim().isEmpty()) {
            Glide.with(holder.itemView.getContext())
                    .load(cartItem.getProductImageUrl())
                    .placeholder(R.drawable.cake_wallpaper)
                    .error(R.drawable.cake_wallpaper)
                    .into(holder.productImageView);
        } else {
            holder.productImageView.setImageResource(R.drawable.cake_wallpaper);
        }

        String details = "Quantity: " + UnitPricing.quantityLabel(
                cartItem.getQuantity(), cartItem.getUnitType());
        if (cartItem.getSelectedFlavor() != null && !cartItem.getSelectedFlavor().trim().isEmpty()) {
            details += " • " + cartItem.getSelectedFlavor();
        }
        if (cartItem.getCakeMessage() != null && !cartItem.getCakeMessage().trim().isEmpty()) {
            details += "\nMessage: " + cartItem.getCakeMessage();
        }
        holder.productDetailsTextView.setText(details);
        holder.quantityTextView.setText(UnitPricing.quantityLabel(
                cartItem.getQuantity(), cartItem.getUnitType()));

        holder.itemTotalTextView.setText(
                String.format(Locale.getDefault(), "Item Total: Rs. %.2f", cartItem.getItemTotal())
        );

        holder.plusButton.setOnClickListener(v -> {
            int current = holder.getBindingAdapterPosition();
            if (listener != null && current != RecyclerView.NO_POSITION)
                listener.onIncrease(current);
        });

        holder.minusButton.setOnClickListener(v -> {
            int current = holder.getBindingAdapterPosition();
            if (listener != null && current != RecyclerView.NO_POSITION)
                listener.onDecrease(current);
        });

        holder.removeButton.setOnClickListener(v -> {
            int current = holder.getBindingAdapterPosition();
            if (listener != null && current != RecyclerView.NO_POSITION)
                listener.onRemove(current);
        });
    }

    @Override
    public int getItemCount() {
        return cartItems != null ? cartItems.size() : 0;
    }

    private String valueOrDefault(String value) {
        return (value == null || value.trim().isEmpty()) ? "-" : value;
    }

    private String formatQuantity(double quantity) {
        if (quantity == (long) quantity) {
            return String.format(Locale.getDefault(), "%d", (long) quantity);
        }
        return String.format(Locale.getDefault(), "%.1f", quantity);
    }

    static class CartViewHolder extends RecyclerView.ViewHolder {

        ImageView productImageView;
        TextView productNameTextView;
        TextView productPriceTextView;
        TextView productDetailsTextView;
        TextView quantityTextView;
        TextView itemTotalTextView;
        ImageButton minusButton;
        ImageButton plusButton;
        ImageButton removeButton;

        public CartViewHolder(@NonNull View itemView) {
            super(itemView);

            productImageView = itemView.findViewById(R.id.cartCakeImageView);
            productNameTextView = itemView.findViewById(R.id.cartCakeNameTextView);
            productPriceTextView = itemView.findViewById(R.id.cartCakePriceTextView);
            productDetailsTextView = itemView.findViewById(R.id.cartCakeDetailsTextView);
            quantityTextView = itemView.findViewById(R.id.quantityTextView);
            itemTotalTextView = itemView.findViewById(R.id.itemTotalTextView);
            minusButton = itemView.findViewById(R.id.minusButton);
            plusButton = itemView.findViewById(R.id.plusButton);
            removeButton = itemView.findViewById(R.id.removeButton);
        }
    }
}
