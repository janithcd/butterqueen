package lk.jiat.butterqueen.payment;

import lk.jiat.butterqueen.BuildConfig;

public final class PayHereConfig {

    private PayHereConfig() {
    }

    // Merchant ID is an SDK identifier. The local value is excluded from Git.
    public static final String MERCHANT_ID = BuildConfig.PAYHERE_MERCHANT_ID;
    // Public HTTPS Firebase Function endpoint, not a secret.
    public static final String NOTIFY_URL = BuildConfig.PAYHERE_NOTIFY_URL;

    // Live payments require server-side verification of PayHere notifications.
    public static final String DEFAULT_CITY = "Colombo";
}
