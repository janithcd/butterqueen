# Butter Queen — reviews, notification inbox, admin categories

These fixes work on Firebase **Spark**, with no Cloud Functions or Blaze upgrade.
However, **publishing Firestore Rules is mandatory**. GitHub changes alone do
not change Firebase's active security rules.

## Root cause: reviews unavailable

The Firebase **live rules copied from the project owner** had a top-level
`match /products/{productId}` that granted product reads but omitted the
nested `match /reviews/{reviewUid}`. Firestore therefore denied the
`products/{id}/reviews` read and the customer's review fetch, leaving Publish
disabled and ratings unavailable.

**Review rules change:** Inside your current `match /products/{productId}`,
paste the updated nested review block from:
`security/product-reviews.rules.fragment`. **Do not paste it at top level**.
That grants public review reads, allows authenticated customer writes only
after a delivered order containing this precise product, and grants **admin-only
deletion** for moderation. A customer may edit their existing review but cannot
edit purchase proof or anybody else's review.

Keep the previously published **PayHere sandbox** order rules, **custom cake**
rules, user notifications, FCM token permissions and other rules. Back up live
rules first. Do not replace full rules with `firestore.rules.example`;
its examples are not automatically identical to the live project.

**Test:** log in as a customer with a delivered order containing Chocolate
Cake; open its Product Details -> Reviews OR delivered receipt -> Rate &
Review. The stars/text form should unlock for that purchased item and publish
a review. Customer who didn't buy that product (or is still awaiting
delivery) can read reviews but cannot publish.

## Firebase Spark notification inbox

On Spark, Cloud Functions-based inbox writes are not deployed by this
repository. The phone's **local** order notification did not previously create
a Firestore `users/{uid}/notifications` record, leaving the in-app inbox
empty. The new `SparkOrderNotifications` derives inbox cards from
Firestore orders, scoped to the signed-in account:
- customers see their own order-created and latest status notices;
- admins see new order notices in Bakery Studio;
- order alert taps go to Notifications;
- badge and read state are stored per-user on this device;
- no client may write server-style notifications to another account.

Limitations: notices appear when the app connects to Firestore, and this is
**not** guaranteed background push delivery on Spark. Only the current order
status (not every historical intermediate status) is available. Read state
is per installation and may reappear on another device.

## Admin categories and reviews

Bakery Studio now has **Manage Categories** and **Manage Customer Reviews**.

Category manager lists existing categories and lets admin edit:
- name, description, optional HTTPS image URL, kg/piece, and visibility;
- names are unique in the loaded catalog;
- a rename also updates linked products' `categoryName` in one batch (up to
  400 products), preserving their stable category IDs;
- a pricing-unit change is blocked if existing linked products could become
  mispriced, and categories are hidden rather than deleted.

Review manager lists product reviews and allows admins to delete inappropriate
ones with confirmation. Admins cannot rewrite customers' stars or comments.
Removal updates displayed averages because ratings are calculated from live
review documents. Rules require `isAdmin()` for deletion.

## Test checklist

1. Deploy updated nested product-review rules, then reopen the app.
2. Open customer Product Details -> Reviews; reviews load rather than
   'Reviews unavailable'.
3. Customer without an eligible delivered product cannot submit.
4. Customer with delivered order containing that exact product can submit
   or edit one 1–5 star/3–800-character review.
5. Admin -> Bakery Studio -> Manage Customer Reviews; confirm reviews load;
   remove a test review and check its product average changes.
6. Admin -> Manage Categories; edit a category and confirm it persists and
   linked product labels remain correct.
7. Place a COD/demo order; tap the phone notification to open in-app
   Notifications; confirm the order notice also appears under the bell.
8. Change the order to Preparing; customer Notifications should show the
   current status when connected to Firestore.

Do not loosen Firestore order/review rules to let a customer claim purchases
or change order status. For a real deployment, use trusted backend payment
verification and server-managed notification history.
