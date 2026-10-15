# Account password recovery — Firebase Spark plan

Customer and admin accounts both sign in through `SignInActivity`. The
**Forgot Password?** link opens `ForgotPasswordActivity` with any already typed
email copied across. The password field on Sign In is cleared before opening.

## User journey

1. Open Butter Queen → Sign In → **Forgot Password?**
2. Enter the Firebase Authentication account email → **Send reset link**.
3. If the request is accepted, the app shows a privacy-safe confirmation
   (the app does **not** disclose whether the email is registered).
4. In the email inbox or junk/spam folder, open the Firebase-issued reset link.
5. Set a new password in Firebase's hosted reset page.
6. Return to the app and sign in using the new password.

The app uses the official `FirebaseAuth.sendPasswordResetEmail(email)` API.
It **does not** send custom OTP emails, does not store passwords, and does not
change Firestore `users` documents or `role` values. No Cloud Functions,
Blaze billing, extra Firestore Rules or mail server is required.

## Firebase Console checks (owner action)

1. Open Firebase Console for the correct Butter Queen Firebase project.
2. **Authentication → Sign-in method**: enable **Email/Password**.
3. **Authentication → Templates → Password reset**: confirm the reset email
   sender, project name, action URL and default message are appropriate.
4. Confirm Firebase Authentication's password reset email is reaching
   the registered mailbox, including spam/junk folders.

In projects with email-enumeration protection, Firebase may return successful
request status even if the email isn't registered. This app intentionally uses
the same generic success message either way to avoid account enumeration.

## Error handling and anti-abuse

- Local input validation for empty / malformed email addresses.
- One request in flight; UI disabled until Firebase responds.
- After an accepted request, a 60-second client retry cooldown (Firebase
  additionally applies server-side limits).
- Clear network and too-many-requests error messages without printing
  sensitive email addresses in logs.
- The old password isn't retained in the Sign In password input when opening
  recovery, and no recovery token is stored by the application.

## Manual test checklist

- [ ] Enter invalid email → validation appears, no reset request.
- [ ] Registered customer email → link arrives and works.
- [ ] Registered admin email → same flow works without changing admin role.
- [ ] Non-existent account email → no account-existence disclosure.
- [ ] Device offline → friendly network error and ability to retry.
- [ ] Repeated tap → one in-flight request; wait message after accepted send.
- [ ] Expired or already-used link → Firebase reset page rejects it; request a
      new link in the app.
- [ ] Sign in with old password after reset → rejected.
- [ ] Sign in with new password → routes to correct Customer/Admin home.

**Note on app lock:** Firebase account password recovery is not a six-digit
on-device app-lock backup-code reset. App-lock state and biometric credentials
remain device-specific.

The automation build can verify the activity and policy unit tests; it cannot
deliver a real email. Test the message end-to-end with your Firebase project.
