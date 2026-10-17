package lk.jiat.butterqueen.fragment;

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

import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

import lk.jiat.butterqueen.activity.OrderReceiptActivity;
import lk.jiat.butterqueen.adapter.OrderAdapter;
import lk.jiat.butterqueen.databinding.FragmentOrdersBinding;
import lk.jiat.butterqueen.model.Order;

public class OrdersFragment extends Fragment {

    private FragmentOrdersBinding binding;
    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firestore;
    private OrderAdapter adapter;
    private final List<Order> orderList = new ArrayList<>();
    private ListenerRegistration listenerRegistration;

    public OrdersFragment() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        binding = FragmentOrdersBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();

        binding.ordersRecyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new OrderAdapter(orderList, order ->
                startActivity(OrderReceiptActivity.intentFor(requireContext(), order.getOrderId())));
        binding.ordersRecyclerView.setAdapter(adapter);

        binding.emptyOrdersBrowseButton.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity)
                ((MainActivity) getActivity()).openCatalog();
        });

        loadOrders();
    }

    private void loadOrders() {
        if (binding == null) return;
        if (listenerRegistration != null) {
            listenerRegistration.remove();
            listenerRegistration = null;
        }
        binding.ordersProgress.setVisibility(View.VISIBLE);
        if (firebaseAuth.getCurrentUser() == null) {
            binding.ordersProgress.setVisibility(View.GONE);
            binding.emptyOrdersGroup.setVisibility(View.VISIBLE);
            binding.emptyOrdersTextView.setText("Please sign in to view your orders");
            binding.ordersRecyclerView.setVisibility(View.GONE);
            return;
        }

        String userId = firebaseAuth.getCurrentUser().getUid();

        listenerRegistration = firestore.collection("orders")
                .whereEqualTo("userId", userId)
                .addSnapshotListener((value, error) -> {
                    if (!isAdded() || binding == null) {
                        return;
                    }
                    binding.ordersProgress.setVisibility(View.GONE);

                    if (error != null) {
                        binding.emptyOrdersGroup.setVisibility(View.VISIBLE);
                        binding.ordersRecyclerView.setVisibility(View.GONE);
                        binding.emptyOrdersTextView.setText(
                                "Couldn't load your orders. They will refresh when you're online.");
                        Toast.makeText(requireContext(), "Could not refresh orders", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    orderList.clear();

                    if (value != null) {
                        for (DocumentSnapshot doc : value.getDocuments()) {
                            Order order = parseOrder(doc);
                            orderList.add(order);
                        }
                    }

                    orderList.sort((left, right) -> Long.compare(
                            right.getCreatedAtMillis(), left.getCreatedAtMillis()));
                    adapter.notifyDataSetChanged();

                    boolean isEmpty = orderList.isEmpty();
                    binding.emptyOrdersGroup.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
                    binding.ordersRecyclerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);

                    if (isEmpty) {
                        binding.emptyOrdersTextView.setText("No orders yet. Your sweet journey starts here.");
                    }
                });
    }

    private Order parseOrder(DocumentSnapshot doc) {
        Order order = doc.toObject(Order.class);

        if (order == null) {
            order = new Order();
        }

        order.setOrderId(doc.getId());

        if (order.getCreatedAtMillis() == 0) {
            Timestamp timestamp = doc.getTimestamp("createdAt");
            if (timestamp != null) {
                order.setCreatedAtMillis(timestamp.toDate().getTime());
            } else {
                order.setCreatedAtMillis(System.currentTimeMillis());
            }
        }

        if (order.getTotalAmount() == 0) {
            Double oldTotal = doc.getDouble("total");
            if (oldTotal != null) {
                order.setTotalAmount(oldTotal);
            }
        }

        if (order.getTotalItems() == 0) {
            Long oldQty = doc.getLong("quantity");
            if (oldQty != null) {
                order.setTotalItems(oldQty.intValue());
            }
        }

        if (order.getOrderStatus() == null || order.getOrderStatus().trim().isEmpty()) {
            order.setOrderStatus("placed");
        }

        if (order.getPaymentMethod() == null || order.getPaymentMethod().trim().isEmpty()) {
            order.setPaymentMethod("cash_on_delivery");
        }

        return order;
    }

    @Override
    public void onDestroyView() {
        if (binding != null) binding.ordersRecyclerView.setAdapter(null);
        super.onDestroyView();

        if (listenerRegistration != null) {
            listenerRegistration.remove();
            listenerRegistration = null;
        }

        binding = null;
    }
}
