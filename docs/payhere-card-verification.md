# PayHere card-order status verification (sandbox)

The admin screen intentionally blocks preparing/delivering a PayHere order
until **the backend**, not the Android SDK, has verified the provider callback.
PayHere returns `status_code=2` and a signed `md5sig` when a Checkout payment
has succeeded. SDK activity success by itself is not reliable proof of payment.

This repository now includes `functions/payHereNotify` (HTTPS Cloud Function),
a checkout callback URL, and a server-verified `paid` transition. **Code
committed to GitHub is not deployment.** This only works once the function,
Firebase secrets, matching Android settings and Firestore security rules are
configured in the correct Firebase project. The code remains sandbox-only.

## Required setup by the Firebase project owner

1. Confirm the correct Firebase CLI project (`firebase use`) and that
   Cloud Functions v2 deployment is allowed. Blaze billing may be required.
2. In your **PayHere sandbox merchant console**, copy its Merchant ID and
   Merchant Secret for the approved Android package `lk.jiat.butterqueen`.
   **Never paste the Merchant Secret into ChatGPT, GitHub, Android,
   `local.properties` or `google-services.json`.**
3. From the project root, on your own computer, set the backend secrets:
   ```sh
   firebase functions:secrets:set PAYHERE_MERCHANT_ID
   firebase functions:secrets:set PAYHERE_MERCHANT_SECRET
   firebase deploy --only functions:payHereNotify
   ```
   The CLI prompts privately for each secret. Both values must match the
   exact PayHere sandbox merchant/app configuration used by Android.
4. Copy the exact `payHereNotify` HTTPS URL printed by Firebase CLI after
   deploying (typically a URL on cloudfunctions.net in `asia-south1`).
   Into your ignored Android Studio `local.properties`:
   ```properties
   PAYHERE_MERCHANT_ID=your_sandbox_merchant_id
   PAYHERE_NOTIFY_URL=https://YOUR-DEPLOYED-FUNCTION-URL
   ```
   Rebuild and install the new APK. This public URL is not a secret.
5. Deploy and verify the intended Firestore security rules from
   `security/firestore.rules.example` in Firebase Console or via your
   reviewed rules deployment process. Clients must NOT be able to set
   `paymentStatus=paid` or edit `paymentVerifiedAt`.
6. Place a **new sandbox card order**, not a live payment. Verify:
   - The order is created in Firestore **before** PayHere is opened, with
     `orderStatus=payment_pending` and `paymentStatus=verification_pending`.
   - The signed callback with matching amount, currency and merchant ID
     changes `paymentStatus` to `paid` and `orderStatus` to `placed`.
   - Admin Overview and Order Manager show Placed/Preparing/Out for delivery/
     Delivered/Cancelled on refresh.
   - Invalid signature, reference or amount cannot mark an order paid.
   - Failed/cancelled SDK actions leave payment unverified and do not release
     the order. Check payment results before retrying to prevent duplicates.

The endpoint accepts form-urlencoded POST notifications, verifies PayHere's
official MD5 signature using secrets accessible only to Cloud Functions,
checks the matching Firestore order and LKR total, then atomically updates
status. It ignores non-success statuses and is safe for duplicate successes.
A cancelled order is not silently restored to `placed` even if a successful
notification arrives later; review it in PayHere and refund as appropriate.

## Existing older stuck orders

Orders placed **before** this code (or before a configured `notify_url`) may
still have `paymentStatus=verification_pending`. They cannot be automatically
assumed paid and this deployment does not retrospectively verify them.

Open the PayHere sandbox Merchant Dashboard, find the exact
`paymentReference`, amount and payment result. If the payment is actually
successful, reconcile it with a trusted backend process (or test again with a
new payment after deploying). Do not modify `paymentStatus` from the Android
app or relax Firestore rules. Never repeat a charge without checking the
merchant transaction history.

## Important production limitation

**Do not use this app for real/live payments yet.** While notifications are
verified, the current Android checkout and order creation still calculate
totals client-side. Before accepting real money, move catalog item/fee
validation, authoritative order creation and amount calculation to a trusted
backend; add provider reconciliation and chargeback/refund handling. Sandbox
merchant credentials cannot be used to validate live payments.

References:
- https://support.payhere.lk/api-%26-mobile-sdk/android-sdk
- https://support.payhere.lk/api-%26-mobile-sdk/checkout-api.I
