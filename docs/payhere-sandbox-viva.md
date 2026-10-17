# PayHere sandbox viva demo — no Firebase Blaze needed

This is a **university-only** alternative to the Cloud Functions PayHere
verification integration. It is intentionally **not** a payment processor.
The phone opens the official PayHere **sandbox** SDK; when that SDK reports
success, the Android **debug** build creates a Firestore order with:

- `paymentMethod: "payhere"`
- `paymentStatus: "sandbox_demo"` (never `paid`)
- `orderStatus: "placed"`

Admin screens can change demo orders through Placed, Preparing,
Out for delivery, Delivered and Cancelled. Receipts clearly indicate that
no real payment was independently verified. The real/release build keeps
the separate server-verified payment path, which requires a deployed HTTPS
notification handler and is **not** suitable for live payments until a
backend authoritatively checks product/order totals.

## Steps

1. Merge the accompanying PR after GitHub Actions builds successfully,
   then `git switch main` and `git pull origin main` in Android Studio.
2. Keep the existing **sandbox Merchant ID** in ignored `local.properties`:
   `PAYHERE_MERCHANT_ID=...`. Do **not** add your Merchant Secret anywhere
   in Android or GitHub. A `PAYHERE_NOTIFY_URL` is **not** required for
   this debug-only demo.
3. **Before changing Firestore rules, copy/back up the rules currently
   deployed** from Firebase Console -> **butter-queen** -> Firestore Database
   -> Rules. They may differ from GitHub's example.
4. In `security/firestore.rules.viva-sandbox.example`, review the
   **orders create/update** permissions. Adapt ONLY those portions into the
   existing rules, preserving other permissions. The sandbox rules allow
   a signed-in customer to create an order explicitly tagged `sandbox_demo`,
   and allow an authorized admin to update its order status. They also
   allow a one-time admin conversion of older `verification_pending`,
   `payment_pending` PayHere orders to `sandbox_demo`, `placed`.
   **WARNING:** Anyone who can write a customer order can claim
   `sandbox_demo` on this project. Do not use these permissions in any
   project connected to real-money checkout.
5. Publish the reviewed rules using Firebase Console (works on Spark).
   This GitHub PR does **not** deploy any Firebase changes.
6. In Android Studio use a **debug** run/build. Complete a test PayHere
   sandbox checkout. After SDK success, an order is created in Firestore
   with `sandbox_demo` status and shown as a demo in its PDF receipt.
   If the SDK is cancelled/failed, no new demo order is created.
7. Open Bakery Studio -> Recent orders or Manage Orders; statuses should
   now be editable. If it still says 'not verified', check the stored
   `paymentStatus` and that the debug APK is installed.
8. For old stuck test orders, admin can choose **Continue as sandbox demo**
   and confirm; the order is clearly flagged *not paid*. Avoid doing this
   for any real-world transaction.

## Costs and limitations

- No Blaze upgrade or Cloud Function deployment required.
- Firebase Spark's own quotas still apply.
- PayHere sandbox never takes real payments.
- Device SDK success is **not** independently verified. It is explicitly
  stored as `sandbox_demo`, excluded from verified PayHere sales revenue.
- Admin 'delivered' is demonstration order progress, not confirmation of
  payment, shipment or cash collection.
- Revert sandbox rules and disable the demo path before any real users,
  payments or production release.

This is intentionally a viva teaching/demonstration mode, not a replacement
for secure merchant payment notification verification.
