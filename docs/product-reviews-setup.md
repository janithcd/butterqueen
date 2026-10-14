# Verified customer product reviews — Butter Queen

## How it works

- Products → Product Details displays **live average rating** and total review count.
- **Read Customer Reviews** opens the ratings and comments for that exact product.
- Orders → **Delivered** order → Order Receipt shows **★ Rate & Review** for
  **each ordered product**. Pending, preparing, cancelled and unverified PayHere
  orders do not offer this option.
- Customer chooses 1–5 stars and writes 3–800 characters; a review is stored
  at `products/{productId}/reviews/{firebaseAuthUid}`.
- There is one review per customer per product. The same customer may edit the
  existing stars/comment from a delivered-order receipt.
- Reviews are labelled **Verified customer** without exposing customer name,
  email or delivery address in the public review UI.
- No Cloud Functions, Storage bucket or Blaze billing is needed.

## REQUIRED owner action: Firestore Rules

**A GitHub commit does not deploy Firestore Rules.** In the Firebase Console,
select the actual `butter-queen` project → Firestore Database → Rules.
Find the existing `match /products/{productId}` block and add the nested
`match /reviews/{reviewUid}` block from:

[`security/product-reviews.rules.fragment`](../security/product-reviews.rules.fragment)

Do NOT paste the fragment at the top level: it needs the surrounding
`productId` match and existing `signedIn()`, `isOwner()` helpers.
**Do NOT replace working custom-cake requests, notifications, orders, or admin
rules** with a repository sample from another date. Backup live rules first.

The Rules independently verify:
1. The review document id equals the authenticated user's UID.
2. The referenced order exists, belongs to that user and is **delivered**.
3. The specified item exists in that exact order and its `productId` matches
   the product whose review is being written.
4. The rating is an integer from 1 to 5; review text is 3–800 characters.
5. Timestamps are server-generated. An edit cannot alter proof of purchase.
6. Neither customers nor admins may delete a review through this policy.

## Test after publishing

1. Sign in as a customer; pick a product that was **not** in any delivered order.
   Read reviews: it must not let the customer publish.
2. Place a Cash on Delivery test order for that product.
3. Confirm that **Placed** status does not offer a review action.
4. Sign in as admin, manage that order and change status to **Delivered**.
5. Sign in as the customer → Orders → Order Receipt → **★ Rate & Review**.
6. Give 4 stars and text **"Delicious and nicely decorated!"**; publish.
7. Reopen Product Details to see the average and review count update.
8. Reopen delivered order receipt and edit the review. The count remains 1.
9. Try signing in as another customer without that delivered product:
   no review submission; their Firestore write must be denied by the rules.
10. Confirm Cash on Delivery and PayHere sandbox logic remains unchanged.

**Testing note:** Ratings are computed by reading reviews for the product.
For this small university demo this is simple and accurate; a large production
app should use a trusted aggregate count/sum maintained server-side.
