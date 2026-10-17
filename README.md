# ButterQueen Android app

ButterQueen is a Java Android cake ordering app. The current code includes Firebase Authentication, Firestore product and cart data, checkout, orders, local-only profile photos, delivery area maps, and **PayHere sandbox** checkout.

## Local setup

1. Open this folder in Android Studio. Keep the generated `local.properties` file outside Git.
2. Add `app/google-services.json` from the correct Firebase Android app, whose package name is `lk.jiat.butterqueen`. This file is intentionally ignored by Git. Do not confuse it with a Firebase Admin service-account JSON, which must never be placed in an Android app.
3. Copy the entries from `local.properties.example` into your existing `local.properties`. Restrict `MAPS_API_KEY` to the **Maps SDK for Android**, package `lk.jiat.butterqueen`, and your signing SHA-1. For a free **debug-only PayHere sandbox viva demo**, set `PAYHERE_MERCHANT_ID` and follow [sandbox viva setup](docs/payhere-sandbox-viva.md); `PAYHERE_NOTIFY_URL` is not needed. For the separate server-verified release path, see [PayHere verification setup](docs/payhere-card-verification.md).
4. Sync Gradle and run the app. Missing local values allow a build, but maps or PayHere checkout will be unavailable.

## Catalog and ordering

- Create the starter categories **Cakes**, **Cupcakes**, and **Brownies** in Bakery Studio, or add custom categories with a chosen price unit. A product's `unitType` is `kg` or `pcs`; `price` always means the price for **1 kg** or **1 piece**.
- Cake products can be marked **Birthday**, **Wedding**, or **Other**. Customers choose cake weight in 0.5 kg steps. Cupcakes and brownies use whole pieces. The cart and order items store both the unit and numeric quantity.
- Existing `box` and `pack` products are hidden from customer browsing until an admin edits the product and confirms its new per-piece price. Remove and re-add old box/pack cart items. Review old product prices before taking orders.
- The admin menu appears only to a user with a trusted `role: "admin"` in their `users/{uid}` document. The activity checks that role from the server as well. Create and audit admin roles using the Firebase Console or a trusted server; customers must never be able to write their own role. **Deploy and test the candidate rules in `security/` before relying on admin access.**
- Bakery Studio adds categories and products, lists recent orders, shows their unit-aware line items, and lets an admin change order status. Verified PayHere transactions need a trusted backend, but the **debug/viva build** supports clearly labeled `sandbox_demo` orders that can move through simulated delivery statuses without Firebase Blaze. See [sandbox viva setup](docs/payhere-sandbox-viva.md). Product images currently accept an HTTPS image URL; uploading product photos directly requires a separate reviewed Storage rule and workflow.

## Payment and data safety

- The client uses the PayHere **sandbox URL only**. For an Android **debug** build, SDK success creates a **demo-only** Firestore order tagged `sandbox_demo` (never `paid`); no real payment or backend verification is implied. A separate [sandbox-only Firestore rule example](security/firestore.rules.viva-sandbox.example) must be reviewed and deliberately applied to enable this. The release path still creates a pending order before PayHere opens.
- The `payHereNotify` Cloud Function now verifies the merchant signature and correct order amount, currency and reference before marking a card order `paid`. **It must be deployed and configured** separately; GitHub source code alone cannot verify any payments. See [PayHere setup and legacy-order guidance](docs/payhere-card-verification.md). Keep live payments disabled until totals are server-authoritative.
- Checkout rechecks current product availability, unit, and price from Firestore before starting an order. This catches stale carts; it is still a client check. A trusted server must recalculate item and delivery totals before charging or fulfilling a real order. Client-submitted totals are not authoritative.
- Review and deploy Firebase Firestore and Storage rules in the actual Firebase project. Candidate rules and their assumptions are in `security/`; they are **not deployed** by this repository.
- Account deletion in the current Android client does not reliably purge nested Firestore data, orders, and profile images. A trusted backend deletion workflow is required before calling that a complete deletion.

## Credential cleanup

The repository's original import included a Maps key in the manifest. The manifest now uses a local build placeholder. **Revoke the old key and create a restricted replacement in Google Cloud.** Removing it from a commit or branch history cannot invalidate copies already made.

## Receipts, settings, and device features

- In **Orders**, tap an order to open its current server-backed receipt and choose **Download PDF** or **Share PDF**. The document picker saves without storage permission; the Android Sharesheet can securely grant temporary access to the receipt PDF to a chosen app, including **Quick Share** for Bluetooth/Wi-Fi transfer on compatible devices. Sharing is initiated by the customer and may disclose name, address and phone number to the recipient. A pending cash-on-delivery or PayHere sandbox order receipt is **not proof of payment**.
- **Biometric app lock** is optional per account on this device. Set a six-digit backup code, then verify with an enrolled fingerprint or face. The app locks when it leaves the foreground. The backup code works if biometrics are unavailable; a forgotten code can be reset online with the Firebase account password.
- The backup code is stored as a salted PBKDF2 verifier with a retry cooldown. Local preferences are excluded from cloud and device backup. This is a device UI lock; Firebase Authentication and Firestore Security Rules still control backend access.
- **Shake to refresh catalog** uses the accelerometer only while Home is visible. It is off by default and disabled on devices without an accelerometer.
- Customer profile photos are saved in **app-private internal storage** under the signed-in account UID. Camera or gallery works without Firebase Storage, billing or Storage Rules. Photos stay on the same device and are removed when the app is uninstalled or its storage is cleared. The customer Profile and navigation drawer display the same local file. See [profile photo notes](docs/customer-profile-photos.md).
- Settings offers follow-device, light, and dark appearance. Help and About provide **0763227525** and **info@butterqueen.com** with call and email actions.
- See the [complete marking rubric evidence and viva test checklist](docs/viva-marking-evidence.md) for criterion-by-criterion code evidence, demonstration instructions and limitations. The [technical report draft](docs/technical-report-draft.md) includes architecture, requirements, data storage, testing templates and honest future-work notes; add real emulator screenshots and observed test results before submission.
