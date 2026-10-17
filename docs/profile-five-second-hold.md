# 5-second hold to delete account — Butter Queen

The **customer Profile** screen requires a continuous touch-and-hold for **five
full seconds** before opening a final confirmation dialog. This demonstrates
Android touch events for the university viva.

## Touch event handling

- `MotionEvent.ACTION_DOWN`: start a monotonic `SystemClock.uptimeMillis()`
  timer, request that the parent scrolling container not intercept the finger,
  and begin updating the button label and progress indicator.
- `ACTION_MOVE`: cancel if the original finger leaves the button bounds.
- `ACTION_POINTER_DOWN`: cancel a multi-touch gesture.
- `ACTION_UP` before five seconds, or `ACTION_CANCEL`: reset text,
  progress and countdown without opening the dialog.
- While the original finger remains down, a main-thread `Handler` updates
  progress. At **5,000 ms**, the user receives long-press haptic feedback and
  `performClick()` opens the final confirmation dialog. The account is
  **not** deleted by the hold alone.
- The button only accepts a `performClick` following a complete hold. A
  normal tap cannot trigger account deletion. The app also provides a
  confirmation-dialog fallback for Android touch-exploration accessibility.
- `onStop()` and `onDestroyView()` cancel the gesture and remove pending
  callbacks to prevent accidentally continuing after leaving the screen.

Pure hold threshold/progress/countdown calculations are covered by
`HoldToDeletePolicyTest`. The gesture is implemented in
`ui/HoldToConfirmTouch.java`.

## Manual demonstration

1. **Use a disposable test account. Deletion is irreversible.**
2. Open Profile and tap Delete: a brief tap must NOT open the dialog.
3. Hold for ~2 seconds and release: the progress resets; nothing is deleted.
4. Hold while moving your finger outside the button: the hold cancels.
5. Hold for 5 seconds without moving away: watch countdown/progress and feel
   haptic feedback; a second confirmation dialog should appear.
6. Choose Cancel: the account remains intact.
7. Only when intentionally testing actual account removal, repeat the hold and
   confirm Delete account. Firebase Auth may require a recent sign-in.

## Existing deletion limitation

The existing deletion code calls `FirebaseUser.delete()` and attempts a
Firestore user-document deletion afterward. That second operation is best
effort and may fail because the account is no longer authenticated, and
Firestore subcollections/orders/reviews are not recursively deleted.

**The dialog explicitly warns that stored records may remain.** This gesture
change does not claim GDPR-complete data removal. Proper full deletion needs
an authenticated, trusted backend cleanup workflow. Do not describe the current
flow as erasing all account data during a viva.
