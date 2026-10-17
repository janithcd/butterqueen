"use strict";

const crypto = require("node:crypto");

/**
 * PayHere Checkout/Mobile SDK server notification verifier.
 * A successful SDK callback is NOT proof of payment.
 *
 * https://support.payhere.lk/api-%26-mobile-sdk/checkout-api.I
 */
function md5Upper(text) {
  return crypto.createHash("md5").update(text, "utf8")
      .digest("hex").toUpperCase();
}

function verifyNotification(fields, merchantId, merchantSecret) {
  const names = ["merchant_id", "order_id", "payhere_amount",
    "payhere_currency", "status_code", "md5sig"];
  if (!merchantId || !merchantSecret ||
      names.some((name) => typeof fields[name] !== "string" ||
          fields[name].length === 0)) {
    return false;
  }
  if (fields.merchant_id !== merchantId || !/^[0-9A-F]{32}$/i.test(fields.md5sig)) {
    return false;
  }

  const payload = fields.merchant_id + fields.order_id +
      fields.payhere_amount + fields.payhere_currency +
      fields.status_code + md5Upper(merchantSecret);
  const expected = Buffer.from(md5Upper(payload), "hex");
  const received = Buffer.from(fields.md5sig, "hex");
  return expected.length === received.length &&
      crypto.timingSafeEqual(expected, received);
}

function amountToCents(value) {
  const str = String(value);
  // Avoid float equality, exponential formats, thousand separators or rounding.
  if (!/^(?:0|[1-9][0-9]*)(?:\.[0-9]{1,2})?$/.test(str)) return null;
  const [whole, part = ""] = str.split(".");
  const cents = Number(whole) * 100 + Number(part.padEnd(2, "0"));
  return Number.isSafeInteger(cents) ? cents : null;
}

function sameAmount(payHereAmount, orderTotal) {
  if (typeof orderTotal !== "number" || !Number.isFinite(orderTotal) ||
      orderTotal < 0) return false;
  const expected = amountToCents(orderTotal.toFixed(2));
  const received = amountToCents(payHereAmount);
  return expected !== null && expected === received;
}

function parseNotificationBody(body) {
  if (body && typeof body === "object" && !Buffer.isBuffer(body)) {
    return body;
  }
  if (typeof body !== "string" && !Buffer.isBuffer(body)) return {};
  return Object.fromEntries(new URLSearchParams(body.toString("utf8")));
}

module.exports = {verifyNotification, sameAmount, parseNotificationBody};
