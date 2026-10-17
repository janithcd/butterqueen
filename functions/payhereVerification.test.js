"use strict";

const test = require("node:test");
const assert = require("node:assert/strict");
const crypto = require("node:crypto");
const {verifyNotification, sameAmount, parseNotificationBody} =
    require("./payhereVerification");

const merchantId = "123456";
const merchantSecret = "sandbox-secret-for-tests";

function signed(overrides = {}) {
  const fields = {
    merchant_id: merchantId,
    order_id: "BQ-FIRESTORE123",
    payhere_amount: "3525.00",
    payhere_currency: "LKR",
    status_code: "2",
    ...overrides,
  };
  const md5 = (input) => crypto.createHash("md5")
      .update(input, "utf8").digest("hex").toUpperCase();
  fields.md5sig = md5(fields.merchant_id + fields.order_id +
      fields.payhere_amount + fields.payhere_currency +
      fields.status_code + md5(merchantSecret));
  return fields;
}

test("valid PayHere notification is signed with the shared secret", () => {
  assert.equal(verifyNotification(signed(), merchantId, merchantSecret), true);
});

test("payment signature rejects changed reference, price, status and merchant", () => {
  for (const overrides of [
    {order_id: "BQ-OTHER"}, {payhere_amount: "1.00"},
    {status_code: "-2"}, {merchant_id: "999"},
  ]) {
    const fields = signed();
    Object.assign(fields, overrides);
    assert.equal(verifyNotification(fields, merchantId, merchantSecret), false);
  }
  assert.equal(verifyNotification(signed(), "wrong-id", merchantSecret), false);
  assert.equal(verifyNotification(signed(), merchantId, "wrong-secret"), false);
  assert.equal(verifyNotification({...signed(), md5sig: "oops"},
      merchantId, merchantSecret), false);
  assert.equal(verifyNotification({}, merchantId, merchantSecret), false);
});

test("amount comparison uses exact cents and rejects unexpected formats", () => {
  assert.equal(sameAmount("3525.00", 3525), true);
  assert.equal(sameAmount("3525.10", 3525.1), true);
  assert.equal(sameAmount("3525.1", 3525.10), true);
  assert.equal(sameAmount("35.25", 3525), false);
  assert.equal(sameAmount("3,525.00", 3525), false);
  assert.equal(sameAmount("3525.009", 3525), false);
  assert.equal(sameAmount("-5.00", -5), false);
  assert.equal(sameAmount("not-a-number", 3525), false);
});

test("parses a form-urlencoded notification", () => {
  assert.deepEqual(parseNotificationBody("order_id=BQ-11&status_code=2"),
      {order_id: "BQ-11", status_code: "2"});
});
