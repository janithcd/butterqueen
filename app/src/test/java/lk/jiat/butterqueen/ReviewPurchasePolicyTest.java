package lk.jiat.butterqueen;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import lk.jiat.butterqueen.data.ReviewPurchasePolicy;

public class ReviewPurchasePolicyTest {
    @Test
    public void onlyDeliveredOrdersOwnedByViewerQualify() {
        assertTrue(ReviewPurchasePolicy.isDeliveredForUser("owner", "owner", "delivered"));
        assertFalse(ReviewPurchasePolicy.isDeliveredForUser("owner", "other", "delivered"));
        assertFalse(ReviewPurchasePolicy.isDeliveredForUser("owner", "owner", "placed"));
        assertFalse(ReviewPurchasePolicy.isDeliveredForUser("owner", "owner", "cancelled"));
        assertFalse(ReviewPurchasePolicy.isDeliveredForUser("", "", "delivered"));
        assertFalse(ReviewPurchasePolicy.isDeliveredForUser(null, "owner", "delivered"));
    }

    @Test
    public void reviewMustTargetExactPurchasedProductId() {
        assertTrue(ReviewPurchasePolicy.matchesProduct("chocolate-cake", "chocolate-cake"));
        assertFalse(ReviewPurchasePolicy.matchesProduct("chocolate-cake", "vanilla-cake"));
        assertFalse(ReviewPurchasePolicy.matchesProduct("", ""));
        assertFalse(ReviewPurchasePolicy.matchesProduct(null, "chocolate-cake"));
        assertFalse(ReviewPurchasePolicy.matchesProduct("chocolate-cake", null));
    }
}
