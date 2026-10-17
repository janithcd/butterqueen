package lk.jiat.butterqueen.fragment;
import lk.jiat.butterqueen.ui.UiFeedback;

import android.content.Intent;
import android.os.Bundle;
import lk.jiat.butterqueen.activity.MainActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.List;
import java.util.Locale;

import lk.jiat.butterqueen.activity.CheckoutActivity;
import lk.jiat.butterqueen.adapter.CartAdapter;
import lk.jiat.butterqueen.data.CartManager;
import lk.jiat.butterqueen.databinding.FragmentCartBinding;
import lk.jiat.butterqueen.model.CartItem;

public class cartFragment extends Fragment {

    private FragmentCartBinding binding;
    private CartAdapter adapter;

    public cartFragment() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentCartBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.cartRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new CartAdapter(CartManager.getCartItems(), new CartAdapter.OnCartActionListener() {
            @Override
            public void onIncrease(int position) {
                CartManager.increaseQuantity(position, new CartManager.SimpleCallback() {
                    @Override
                    public void onSuccess() {
                    }

                    @Override
                    public void onFailure(String message) {
                        if (isAdded()) {
                            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }

            @Override
            public void onDecrease(int position) {
                CartManager.decreaseQuantity(position, new CartManager.SimpleCallback() {
                    @Override
                    public void onSuccess() {
                    }

                    @Override
                    public void onFailure(String message) {
                        if (isAdded()) {
                            Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                        }
                    }
                });
            }

            @Override
            public void onRemove(int position) {
                CartManager.removeItem(position, new CartManager.SimpleCallback() {
                    @Override
                    public void onSuccess() {
                        if (binding != null) UiFeedback.success(binding.getRoot(),
                                "Treat removed from your bag");
                    }

                    @Override
                    public void onFailure(String message) {
                        if (binding != null) UiFeedback.error(binding.getRoot(), message);
                    }
                });
            }
        });

        binding.cartRecyclerView.setAdapter(adapter);

        binding.emptyCartBrowseButton.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity)
                ((MainActivity) getActivity()).openCatalog();
        });

        binding.checkoutButton.setOnClickListener(v -> {
            if (CartManager.isEmpty()) {
                Toast.makeText(requireContext(), "Your cart is empty", Toast.LENGTH_SHORT).show();
            } else {
                startActivity(new Intent(requireContext(), CheckoutActivity.class));
            }
        });

        updateCartSummary();
    }

    @Override
    public void onStart() {
        super.onStart();
        startCartListener();
    }

    @Override
    public void onStop() {
        super.onStop();
        CartManager.stopCartListener();
    }

    private void startCartListener() {
        CartManager.startCartListener(new CartManager.CartLoadListener() {
            @Override
            public void onCartUpdated(List<CartItem> cartItems) {
                if (!isAdded() || binding == null) return;

                adapter.notifyDataSetChanged();
                updateCartSummary();
            }

            @Override
            public void onError(String message) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateCartSummary() {
        int totalItems = CartManager.getTotalItems();
        double totalPrice = CartManager.getTotalPrice();

        binding.totalItemsTextView.setText("Products: " + totalItems);
        binding.totalPriceTextView.setText(
                String.format(Locale.getDefault(), "Total: Rs. %.2f", totalPrice)
        );

        boolean isEmpty = CartManager.isEmpty();
        binding.emptyCartGroup.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        binding.cartRecyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        binding.checkoutButton.setEnabled(!isEmpty);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (binding != null) binding.cartRecyclerView.setAdapter(null);
        binding = null;
    }
}