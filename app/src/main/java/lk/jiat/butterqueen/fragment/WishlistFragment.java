package lk.jiat.butterqueen.fragment;

import android.content.Intent;
import android.os.Bundle;
import lk.jiat.butterqueen.activity.MainActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import lk.jiat.butterqueen.activity.ProductDetailsActivity;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import java.util.ArrayList;
import java.util.List;

import lk.jiat.butterqueen.adapter.ProductAdapter;
import lk.jiat.butterqueen.data.WishlistManager;
import lk.jiat.butterqueen.databinding.FragmentWishlistBinding;
import lk.jiat.butterqueen.model.Product;

public class WishlistFragment extends Fragment {

    private FragmentWishlistBinding binding;
    private ProductAdapter adapter;
    private final List<Product> wishlistProducts = new ArrayList<>();

    public WishlistFragment() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentWishlistBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.wishlistRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new ProductAdapter(
                wishlistProducts,
                product -> {
                    Intent intent = new Intent(requireContext(), ProductDetailsActivity.class);
                    intent.putExtra("product_id", product.getId());
                    startActivity(intent);
                },
                (product, isWishlisted, position) -> refreshWishlist()
        );

        binding.wishlistRecyclerView.setAdapter(adapter);

        binding.emptyWishlistBrowseButton.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity)
                ((MainActivity) getActivity()).openCatalog();
        });

        refreshWishlist();
    }

    private void refreshWishlist() {
        wishlistProducts.clear();
        wishlistProducts.addAll(WishlistManager.getWishlistProducts());

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }

        boolean isEmpty = wishlistProducts.isEmpty();
        binding.emptyWishlistGroup.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        binding.wishlistRecyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshWishlist();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (binding != null) binding.wishlistRecyclerView.setAdapter(null);
        binding = null;
    }
}