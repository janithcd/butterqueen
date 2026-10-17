package lk.jiat.butterqueen.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

import lk.jiat.butterqueen.activity.MainActivity;
import lk.jiat.butterqueen.adapter.CategoryAdapter;
import lk.jiat.butterqueen.databinding.FragmentCategoryBinding;
import lk.jiat.butterqueen.model.Category;

public class CategoryFragment extends Fragment {

    private FragmentCategoryBinding binding;
    private CategoryAdapter adapter;
    private final List<Category> categoryList = new ArrayList<>();
    private FirebaseFirestore firestore;

    public CategoryFragment() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentCategoryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        firestore = FirebaseFirestore.getInstance();

        binding.recyclerViewCategories.setLayoutManager(new GridLayoutManager(requireContext(), 2));

        adapter = new CategoryAdapter(categoryList, category -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).showCategoryProducts(category);
            }
        });
        binding.recyclerViewCategories.setAdapter(adapter);
        binding.categoriesRetry.setOnClickListener(v -> loadCategories());
    }

    @Override
    public void onResume() {
        super.onResume();
        if (binding != null && firestore != null) loadCategories();
    }

    private void loadCategories() {
        if (binding == null) return;
        binding.categoriesProgress.setVisibility(View.VISIBLE);
        binding.categoriesEmptyState.setVisibility(View.GONE);
        binding.categoriesRetry.setVisibility(View.GONE);
        firestore.collection("categories")
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    if (binding == null) return;
                    binding.categoriesProgress.setVisibility(View.GONE);
                    categoryList.clear();

                    for (DocumentSnapshot doc : queryDocumentSnapshots.getDocuments()) {
                        Category category = doc.toObject(Category.class);
                        if (category != null && !Boolean.FALSE.equals(doc.getBoolean("active"))) {
                            category.setId(doc.getId());
                            categoryList.add(category);
                        }
                    }

                    adapter.notifyDataSetChanged();

                    boolean isEmpty = categoryList.isEmpty();
                    binding.categoriesEmptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                    binding.recyclerViewCategories.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
                    binding.categoriesFeedback.setText("No categories yet. Check back soon.");
                    binding.categoriesRetry.setVisibility(View.GONE);
                })
                .addOnFailureListener(e -> {
                    if (binding == null) return;
                    binding.categoriesProgress.setVisibility(View.GONE);
                    binding.categoriesEmptyState.setVisibility(View.VISIBLE);
                    binding.categoriesFeedback.setText("Couldn't load categories. Please check your connection.");
                    binding.categoriesRetry.setVisibility(View.VISIBLE);
                    binding.recyclerViewCategories.setVisibility(View.GONE);
                });
    }

    @Override
    public void onDestroyView() {
        if (binding != null) binding.recyclerViewCategories.setAdapter(null);
        super.onDestroyView();
        binding = null;
    }
}