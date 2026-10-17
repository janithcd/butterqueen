package lk.jiat.butterqueen.data;

/** Pure client-side preview; Firebase Security Rules enforce review ownership. */
public final class ReviewPurchasePolicy {
    private ReviewPurchasePolicy() { }

    public static boolean isDeliveredForUser(String uid, String ownerUid, String status) {
        return uid != null && !uid.isEmpty() && uid.equals(ownerUid)
                && "delivered".equals(status);
    }

    public static boolean matchesProduct(String selectedId, String orderedProductId) {
        return selectedId != null && !selectedId.isEmpty()
                && selectedId.equals(orderedProductId);
    }
}
