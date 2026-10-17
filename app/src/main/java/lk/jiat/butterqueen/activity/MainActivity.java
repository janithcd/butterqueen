
        package lk.jiat.butterqueen.activity;

import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import lk.jiat.butterqueen.fragment.DeliveryAreasFragment;
import lk.jiat.butterqueen.fragment.HomeFragment;
import lk.jiat.butterqueen.fragment.HelpFragment;
import lk.jiat.butterqueen.fragment.OrdersFragment;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

import lk.jiat.butterqueen.AppLockGate;
import lk.jiat.butterqueen.data.LocalProfilePhotoStore;
import lk.jiat.butterqueen.notification.PushTokenRegistrar;
import lk.jiat.butterqueen.R;
import lk.jiat.butterqueen.fragment.AboutFragment;
import lk.jiat.butterqueen.fragment.CategoryFragment;
import lk.jiat.butterqueen.fragment.DeliveryAreasFragment;
import lk.jiat.butterqueen.fragment.HelpFragment;
import lk.jiat.butterqueen.fragment.OrdersFragment;
import lk.jiat.butterqueen.fragment.ProfileFragment;
import lk.jiat.butterqueen.fragment.SettingFragment;
import lk.jiat.butterqueen.fragment.WishlistFragment;
import lk.jiat.butterqueen.fragment.cartFragment;
import lk.jiat.butterqueen.fragment.messageFragment;
import lk.jiat.butterqueen.model.Category;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private MaterialToolbar toolbar;
    private BottomNavigationView bottomNavigationView;
    private ActionBarDrawerToggle toggle;
    private boolean syncingBottomNavigation;

    // Network connectivity monitoring
    private ConnectivityManager connectivityManager;
    private ConnectivityManager.NetworkCallback networkCallback;
    private Snackbar offlineSnackbar;
    private boolean networkCallbackRegistered = false;

    // Battery receiver: active only while this Activity is visible.
    private boolean batteryReceiverRegistered = false;
    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            String action = intent.getAction();
            String message;
            if (Intent.ACTION_BATTERY_LOW.equals(action)) {
                message = "Battery low. Please charge your device.";
            } else if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
                message = "Charger connected.";
            } else if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
                message = "Charger disconnected.";
            } else {
                return;
            }
            View root = findViewById(R.id.mainContent);
            if (root != null) {
                Snackbar.make(root, message, Snackbar.LENGTH_LONG).show();
            }
        }
    };

    private FirebaseAuth firebaseAuth;
    private FirebaseFirestore firebaseFirestore;

    private ImageView headerAvatar;
    private TextView headerName;
    private TextView headerEmail;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        connectivityManager = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE);

        firebaseAuth = FirebaseAuth.getInstance();
        firebaseFirestore = FirebaseFirestore.getInstance();
        PushTokenRegistrar.register(this);

        drawerLayout = findViewById(R.id.drawerLayout);
        navigationView = findViewById(R.id.navigationView);
        toolbar = findViewById(R.id.toolbar);
        bottomNavigationView = findViewById(R.id.bottomNavigation);

        setSupportActionBar(toolbar);

        toggle = new ActionBarDrawerToggle(
                this,
                drawerLayout,
                toolbar,
                R.string.open_drawer,
                R.string.close_drawer
        );

        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        navigationView.setNavigationItemSelectedListener(this);
        navigationView.getMenu().findItem(R.id.nav_admin).setVisible(false);
        updateAuthMenu();

        initDrawerHeader();
        listenToDrawerHeaderUser();

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainContent), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        bottomNavigationView.setOnItemSelectedListener(item -> {
            if (syncingBottomNavigation) return true;
            int id = item.getItemId();

            if (id == R.id.bottom_home) {
                loadFragment(new HomeFragment(), "Home");
                return true;


            } else if (id == R.id.bottom_cart) {
                loadFragment(new cartFragment(), "Cart");
                return true;

            } else if (id == R.id.bottom_wishlist) {
                loadFragment(new WishlistFragment(), "Wishlist");
                return true;

            } else if (id == R.id.bottom_profile) {
                loadFragment(new ProfileFragment(), "Profile");
                return true;
            }

            return false;
        });

        if (savedInstanceState == null) {
            if (getIntent().getBooleanExtra("open_orders", false)) {
                loadFragment(new OrdersFragment(), "Orders");
                getIntent().removeExtra("open_orders");
            } else {
                loadFragment(new HomeFragment(), "Home");
            }
            navigationView.setCheckedItem(R.id.nav_home);
        } else {
            Fragment restored = getSupportFragmentManager().findFragmentById(R.id.contentFrame);
            if (restored != null) {
                syncBottomNavigation(restored);
                syncDrawerSelection(restored);
                toolbar.setTitle(titleFor(restored));
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent != null && intent.getBooleanExtra("open_orders", false)) {
            loadFragment(new OrdersFragment(), "Orders");
            intent.removeExtra("open_orders");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshLocalAvatar();
        if (navigationView != null) {
            updateAuthMenu();
            Fragment current = getSupportFragmentManager().findFragmentById(R.id.contentFrame);
            if (current != null) syncDrawerSelection(current);
        }
    }

    private void updateAuthMenu() {
        boolean signedIn = firebaseAuth.getCurrentUser() != null;
        navigationView.getMenu().findItem(R.id.nav_login).setVisible(!signedIn);
        navigationView.getMenu().findItem(R.id.nav_logout).setVisible(signedIn);
        if (!signedIn) navigationView.getMenu().findItem(R.id.nav_admin).setVisible(false);
    }

    private void initDrawerHeader() {
        if (navigationView.getHeaderCount() > 0) {
            View headerView = navigationView.getHeaderView(0);
            headerAvatar = headerView.findViewById(R.id.headerAvatar);
            headerName = headerView.findViewById(R.id.headerName);
            headerEmail = headerView.findViewById(R.id.headerEmail);
        }
    }

    /** Use the same app-private photo as Customer Profile, including after an edit. */
    public void refreshLocalAvatar() {
        if (headerAvatar == null) return;
        String uid = firebaseAuth == null || firebaseAuth.getCurrentUser() == null
                ? null : firebaseAuth.getCurrentUser().getUid();
        LocalProfilePhotoStore.display(this, uid, headerAvatar);
    }

    private void listenToDrawerHeaderUser() {
        if (headerAvatar == null || headerName == null || headerEmail == null) {
            return;
        }

        if (firebaseAuth.getCurrentUser() == null) {
            updateAuthMenu();
            headerName.setText("Guest User");
            headerEmail.setText("Please sign in");
            headerAvatar.setImageResource(R.drawable.account_circle_24px);
            return;
        }

        String userId = firebaseAuth.getCurrentUser().getUid();

        firebaseFirestore.collection("users")
                .document(userId)
                .addSnapshotListener(this, (documentSnapshot, error) -> {
                    if (error != null) {
                        return;
                    }

                    if (documentSnapshot != null && documentSnapshot.exists()) {
                        String firstName = documentSnapshot.getString("firstName");
                        String lastName = documentSnapshot.getString("lastName");
                        String email = documentSnapshot.getString("email");

                        String fullName = ((firstName != null ? firstName : "") + " " +
                                (lastName != null ? lastName : "")).trim();

                        if (fullName.isEmpty()) {
                            fullName = "ButterQueen User";
                        }

                        if ("admin".equals(documentSnapshot.getString("role"))) {
                            // Old customer notification routes cannot leave an admin in customer UI.
                            Intent destination = new Intent(this, AdminOverviewActivity.class);
                            destination.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                    | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                            startActivity(destination);
                            finish();
                            return;
                        }
                        navigationView.getMenu().findItem(R.id.nav_admin).setVisible(false);
                        headerName.setText(fullName);
                        headerEmail.setText(email != null ? email : "");

                        refreshLocalAvatar();
                    }
                });
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.nav_home) {
            loadFragment(new HomeFragment(), "Home");

        } else if (id == R.id.nav_profile) {
            loadFragment(new ProfileFragment(), "Profile");

        } else if (id == R.id.nav_orders) {
            loadFragment(new OrdersFragment(), "Orders");

        } else if (id == R.id.nav_admin) {
            startActivity(new Intent(this, AdminOverviewActivity.class));

        } else if (id == R.id.nav_category) {
            loadFragment(new CategoryFragment(), "Category");

        } else if (id == R.id.nav_wishlist) {
            loadFragment(new WishlistFragment(), "Wishlist");

        } else if (id == R.id.nav_cart) {
            loadFragment(new cartFragment(), "Cart");

        } else if (id == R.id.nav_delivery_areas) {
            loadFragment(new DeliveryAreasFragment(), "Delivery Areas");


        } else if (id == R.id.nav_message) {
            loadFragment(new messageFragment(), "Contact");

        } else if (id == R.id.nav_setting) {
            loadFragment(new SettingFragment(), "Settings");

        } else if (id == R.id.nav_help) {
            openHelpScreen();

        } else if (id == R.id.nav_about) {
            openAboutScreen();

        } else if (id == R.id.nav_login) {
            Intent intent = new Intent(MainActivity.this, SignInActivity.class);
            startActivity(intent);

        } else if (id == R.id.nav_logout) {
            showLogoutDialog();
        }

        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    public void showCategoryProducts(Category category) {
        HomeFragment fragment = new HomeFragment();
        Bundle args = new Bundle();
        args.putString("category_id", category.getId());
        args.putString("category_name", category.getName());
        args.putString("category_unit", category.getUnitType());
        fragment.setArguments(args);
        loadFragment(fragment, category.getName());
    }

    /** Shared, accessible browse action for empty cart/wishlist/order states. */
    public void openCatalog() {
        loadFragment(new HomeFragment(), "Home");
        if (drawerLayout != null) drawerLayout.closeDrawer(GravityCompat.START);
    }

    public void openHelpScreen() {
        loadFragment(new HelpFragment(), "Help");
    }

    public void openAboutScreen() {
        loadFragment(new AboutFragment(), "About");
    }

    private void loadFragment(Fragment fragment, String title) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.contentFrame, fragment)
                .commit();

        toolbar.setTitle(title);
        syncBottomNavigation(fragment);
        syncDrawerSelection(fragment);
    }

    private void syncBottomNavigation(Fragment fragment) {
        int itemId = fragment instanceof HomeFragment ? R.id.bottom_home
                : fragment instanceof cartFragment ? R.id.bottom_cart
                : fragment instanceof WishlistFragment ? R.id.bottom_wishlist
                : fragment instanceof ProfileFragment ? R.id.bottom_profile : 0;
        bottomNavigationView.setVisibility(itemId == 0 ? View.GONE : View.VISIBLE);
        if (itemId != 0 && bottomNavigationView.getSelectedItemId() != itemId) {
            syncingBottomNavigation = true;
            bottomNavigationView.setSelectedItemId(itemId);
            syncingBottomNavigation = false;
        }
    }

    private void syncDrawerSelection(Fragment fragment) {
        int itemId = fragment instanceof HomeFragment ? R.id.nav_home
                : fragment instanceof cartFragment ? R.id.nav_cart
                : fragment instanceof WishlistFragment ? R.id.nav_wishlist
                : fragment instanceof ProfileFragment ? R.id.nav_profile
                : fragment instanceof CategoryFragment ? R.id.nav_category
                : fragment instanceof OrdersFragment ? R.id.nav_orders
                : fragment instanceof DeliveryAreasFragment ? R.id.nav_delivery_areas
                : fragment instanceof SettingFragment ? R.id.nav_setting
                : fragment instanceof HelpFragment ? R.id.nav_help
                : fragment instanceof AboutFragment ? R.id.nav_about
                : fragment instanceof messageFragment ? R.id.nav_message : 0;
        if (itemId != 0) navigationView.setCheckedItem(itemId);
    }

    private String titleFor(Fragment fragment) {
        if (fragment instanceof cartFragment) return "Cart";
        if (fragment instanceof WishlistFragment) return "Wishlist";
        if (fragment instanceof ProfileFragment) return "Profile";
        if (fragment instanceof CategoryFragment) return "Category";
        if (fragment instanceof OrdersFragment) return "Orders";
        if (fragment instanceof DeliveryAreasFragment) return "Delivery Areas";
        if (fragment instanceof SettingFragment) return "Settings";
        if (fragment instanceof HelpFragment) return "Help";
        if (fragment instanceof AboutFragment) return "About";
        if (fragment instanceof messageFragment) return "Contact";
        return "Home";
    }

    private void showLogoutDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Log Out")
                .setMessage("Are you sure do you want to log out?")
                .setCancelable(true)
                .setPositiveButton("Yes", (dialog, which) -> {
                    AppLockGate.lockNow();
                    PushTokenRegistrar.unregister(this);
                    firebaseAuth.signOut();

                    Toast.makeText(MainActivity.this, "Logged out successfully", Toast.LENGTH_SHORT).show();

                    Intent intent = new Intent(MainActivity.this, SignInActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            Fragment current = getSupportFragmentManager()
                    .findFragmentById(R.id.contentFrame);
            if (current != null && !(current instanceof HomeFragment)) {
                // Secondary destinations are root-level fragment replacements, not
                // back-stack entries. Return to catalog instead of exiting the app.
                openCatalog();
            } else {
                super.onBackPressed();
            }
        }
    }

    // Checks whether Android has validated an internet connection.
    private boolean hasInternetConnection() {
        if (connectivityManager == null) return false;
        Network network = connectivityManager.getActiveNetwork();
        if (network == null) return false;
        NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
        return capabilities != null
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                && capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
    }

    // Snackbar operations are always performed on the main UI thread.
    private void updateNetworkStatus() {
        if (hasInternetConnection()) {
            if (offlineSnackbar != null) {
                offlineSnackbar.dismiss();
                offlineSnackbar = null;
            }
        } else if (offlineSnackbar == null) {
            offlineSnackbar = Snackbar.make(
                    findViewById(R.id.mainContent),
                    "No Internet Connection. Please check your network.",
                    Snackbar.LENGTH_INDEFINITE
            );
            offlineSnackbar.show();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!batteryReceiverRegistered) {
            IntentFilter batteryFilter = new IntentFilter();
            batteryFilter.addAction(Intent.ACTION_BATTERY_LOW);
            batteryFilter.addAction(Intent.ACTION_POWER_CONNECTED);
            batteryFilter.addAction(Intent.ACTION_POWER_DISCONNECTED);
            ContextCompat.registerReceiver(this, batteryReceiver, batteryFilter,
                    ContextCompat.RECEIVER_NOT_EXPORTED);
            batteryReceiverRegistered = true;
        }
        if (connectivityManager != null && !networkCallbackRegistered) {
            networkCallback = new ConnectivityManager.NetworkCallback() {
                @Override
                public void onAvailable(@NonNull Network network) {
                    runOnUiThread(() -> updateNetworkStatus());
                }

                @Override
                public void onLost(@NonNull Network network) {
                    runOnUiThread(() -> updateNetworkStatus());
                }

                @Override
                public void onCapabilitiesChanged(@NonNull Network network,
                                                  @NonNull NetworkCapabilities capabilities) {
                    runOnUiThread(() -> updateNetworkStatus());
                }
            };
            connectivityManager.registerDefaultNetworkCallback(networkCallback);
            networkCallbackRegistered = true;
        }
        updateNetworkStatus();
    }

    @Override
    protected void onStop() {
        if (batteryReceiverRegistered) {
            unregisterReceiver(batteryReceiver);
            batteryReceiverRegistered = false;
        }
        if (networkCallbackRegistered && connectivityManager != null) {
            connectivityManager.unregisterNetworkCallback(networkCallback);
            networkCallbackRegistered = false;
        }
        if (offlineSnackbar != null) {
            offlineSnackbar.dismiss();
            offlineSnackbar = null;
        }
        super.onStop();
    }

}


