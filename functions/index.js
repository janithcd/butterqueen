"use strict";

/**
 * Notifications are created ONLY by trusted Cloud Functions after Firestore events.
 * Clients can read only their own inbox and mark messages read.
 * Deploying v2 Firestore triggers requires Firebase Blaze billing.
 */
const {onDocumentCreated, onDocumentUpdated} =
    require("firebase-functions/v2/firestore");
const {onRequest} = require("firebase-functions/v2/https");
const {defineSecret} = require("firebase-functions/params");
const {FieldValue} = require("firebase-admin/firestore");
const {verifyNotification, sameAmount, parseNotificationBody} =
    require("./payhereVerification");
const {initializeApp} = require("firebase-admin/app");
const {getFirestore} = require("firebase-admin/firestore");
const {getMessaging} = require("firebase-admin/messaging");

initializeApp();
const db = getFirestore();
const messaging = getMessaging();
const REGION = "asia-south1";
const PAYHERE_MERCHANT_ID = defineSecret("PAYHERE_MERCHANT_ID");
const PAYHERE_MERCHANT_SECRET = defineSecret("PAYHERE_MERCHANT_SECRET");

/**
 * Public HTTPS PayHere notify_url. Only signed PayHere notifications can
 * promote a card order to "paid". Merchant credentials never enter Android.
 * Deploy and configure both Firebase secrets before making sandbox payments.
 */
exports.payHereNotify = onRequest({
  region: REGION,
  secrets: [PAYHERE_MERCHANT_ID, PAYHERE_MERCHANT_SECRET],
}, async (req, res) => {
  if (req.method !== "POST") {
    res.status(405).send("Method not allowed");
    return;
  }
  if (!String(req.headers["content-type"] || "").toLowerCase()
      .startsWith("application/x-www-form-urlencoded")) {
    res.status(415).send("Expected form-urlencoded notification");
    return;
  }

  const fields = parseNotificationBody(req.body || req.rawBody);
  if (!verifyNotification(fields, PAYHERE_MERCHANT_ID.value(),
      PAYHERE_MERCHANT_SECRET.value())) {
    res.status(403).send("Invalid payment signature");
    return;
  }

  // Successful Checkout payment only. A failed/pending/authorized status
  // must never release a product order for fulfilment.
  if (fields.status_code !== "2") {
    res.status(200).send("Payment not completed");
    return;
  }
  if (fields.payhere_currency !== "LKR") {
    res.status(409).send("Currency mismatch");
    return;
  }
  if (fields.order_id.length > 160) {
    res.status(400).send("Invalid order reference");
    return;
  }

  try {
    const matchingOrders = await db.collection("orders")
        .where("paymentReference", "==", fields.order_id).limit(2).get();
    if (matchingOrders.size !== 1) {
      // A callback can legitimately arrive before an order is committed by
      // a previous app version. Do not mark an unknown order as paid.
      res.status(503).send("Order not ready for verification");
      return;
    }
    const ref = matchingOrders.docs[0].ref;
    await db.runTransaction(async (tx) => {
      const snapshot = await tx.get(ref);
      if (!snapshot.exists) throw new Error("Order no longer exists");
      const order = snapshot.data();
      if (order.paymentMethod !== "payhere" ||
          order.paymentReference !== fields.order_id ||
          !sameAmount(fields.payhere_amount, order.totalAmount)) {
        const error = new Error("Order payment data mismatch");
        error.code = "PAYMENT_MISMATCH";
        throw error;
      }
      if (order.paymentStatus === "paid") return; // Duplicate webhook.
      if (order.paymentStatus === "chargeback") return; // Never re-enable it.

      const updates = {
        paymentStatus: "paid",
        paymentVerifiedAt: FieldValue.serverTimestamp(),
      };
      if (typeof fields.payment_id === "string" &&
          fields.payment_id.length <= 160) {
        updates.payHerePaymentId = fields.payment_id;
      }
      // Respect an order the admin already cancelled; a real paid but
      // cancelled order may need an external refund.
      if (order.orderStatus === "payment_pending") {
        updates.orderStatus = "placed";
      }
      tx.update(ref, updates);
    });
    res.status(200).send("Verified");
  } catch (error) {
    console.error("PayHere payment verification failed", {code: error.code || "unknown"});
    res.status(error.code === "PAYMENT_MISMATCH" ? 409 : 500)
        .send("Payment verification could not be completed");
  }
});


function statusLabel(s) {
  const known = {
    placed: "Placed",
    preparing: "Preparing",
    out_for_delivery: "Out for delivery",
    delivered: "Delivered",
    cancelled: "Cancelled",
    payment_pending: "Payment pending",
    pending: "Pending",
    reviewing: "Reviewing",
    quoted: "Quoted",
    approved: "Approved",
    rejected: "Rejected",
  };
  return known[s] || "Updated";
}

async function push(uid, event) {
  const tokenDocs = await db.collection("users").doc(uid)
      .collection("pushTokens").get();
  const valid = tokenDocs.docs
      .map((doc) => ({doc, token: doc.get("token")}))
      .filter((entry) => typeof entry.token === "string" && entry.token.length > 20);
  for (let from = 0; from < valid.length; from += 500) {
    const batch = valid.slice(from, from + 500);
    const response = await messaging.sendEachForMulticast({
      tokens: batch.map((row) => row.token),
      data: {
        title: event.title,
        body: event.body,
        type: event.type,
        targetId: event.targetId,
        recipientUid: uid,
      },
      android: {priority: "high"},
    });
    const cleanups = [];
    response.responses.forEach((result, i) => {
      const code = result.error && result.error.code;
      if (code === "messaging/registration-token-not-registered" ||
          code === "messaging/invalid-registration-token") {
        cleanups.push(batch[i].doc.ref.delete());
      }
    });
    await Promise.all(cleanups);
  }
}

async function notify(uid, id, fields) {
  if (!uid || typeof uid !== "string") return;
  const ref = db.collection("users").doc(uid)
      .collection("notifications").doc(id);
  const payload = {
    title: fields.title,
    body: fields.body,
    type: fields.type,
    targetId: fields.targetId || "",
    read: false,
    createdAtMillis: Date.now(),
  };
  try {
    // create() provides at-most-once event record per user; retries cannot
    // mark an already-read notification unread again.
    await ref.create(payload);
  } catch (error) {
    if (error.code === 6 || error.code === "already-exists") return;
    throw error;
  }
  try {
    await push(uid, payload);
  } catch (error) {
    // Inbox is authoritative; a transient FCM failure must not undo delivery.
    console.error("FCM delivery error", {uid, id, error: String(error)});
  }
}

async function admins() {
  const users = await db.collection("users").where("role", "==", "admin").get();
  return users.docs.map((u) => u.id);
}

async function allCustomers() {
  // Small university demo dataset; production deployments should use
  // scalable paging/fan-out and an opted-in subscription model.
  const users = await db.collection("users").get();
  return users.docs.filter((u) => u.get("role") !== "admin").map((u) => u.id);
}

async function sendInGroups(recipients, id, payload) {
  for (let i = 0; i < recipients.length; i += 10) {
    await Promise.all(recipients.slice(i, i + 10)
        .map((uid) => notify(uid, id, payload)));
  }
}

exports.onOrderPlaced = onDocumentCreated(
    {document: "orders/{orderId}", region: REGION},
    async (event) => {
      const data = event.data && event.data.data();
      if (!data) return;
      await sendInGroups(await admins(), "new_order_" + event.params.orderId, {
        type: "new_order",
        title: "New customer order",
        body: "A new order is waiting in Bakery Studio.",
        targetId: event.params.orderId,
      });
    });

exports.onOrderStatusChanged = onDocumentUpdated(
    {document: "orders/{orderId}", region: REGION},
    async (event) => {
      if (!event.data) return;
      const before = event.data.before.data();
      const after = event.data.after.data();
      if (!before || !after || before.orderStatus === after.orderStatus ||
          !after.userId) return;
      // A CloudEvent id identifies this change even if status changes to the
      // same value again later. Event IDs are generated by Firebase, not clients.
      await notify(after.userId, "order_status_" + event.id, {
        type: "order_status",
        title: "Order status updated",
        body: "Your order is now " + statusLabel(after.orderStatus) + ".",
        targetId: event.params.orderId,
      });
    });

exports.onProductPublished = onDocumentCreated(
    {document: "products/{productId}", region: REGION},
    async (event) => {
      const data = event.data && event.data.data();
      if (!data || data.available === false) return;
      const name = String(data.name || "a new bakery item").slice(0, 70);
      await sendInGroups(await allCustomers(), "new_product_" + event.params.productId, {
        type: "new_product",
        title: "Fresh from Butter Queen!",
        body: "Check out " + name + " in our new collection.",
        targetId: event.params.productId,
      });
    });

exports.onCustomRequestPlaced = onDocumentCreated(
    {document: "customCakeRequests/{requestId}", region: REGION},
    async (event) => {
      if (!event.data) return;
      await sendInGroups(await admins(), "custom_request_" + event.params.requestId, {
        type: "custom_request",
        title: "New custom cake request",
        body: "A customer has sent a new cake design enquiry.",
        targetId: event.params.requestId,
      });
    });

exports.onCustomRequestStatusChanged = onDocumentUpdated(
    {document: "customCakeRequests/{requestId}", region: REGION},
    async (event) => {
      if (!event.data) return;
      const before = event.data.before.data();
      const after = event.data.after.data();
      if (!before || !after || before.status === after.status || !after.userId) return;
      await notify(after.userId, "custom_status_" + event.id, {
        type: "custom_status",
        title: "Custom cake request updated",
        body: "Your cake request is now " + statusLabel(after.status) + ".",
        targetId: event.params.requestId,
      });
    });
