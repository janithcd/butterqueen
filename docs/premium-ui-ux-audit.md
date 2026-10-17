# Butter Queen — premium UI/UX audit and device QA (October 2026)

## Design principles

- **Warm, consistent Material 3:** Butter Queen's existing rose, pastry-cream,
  and espresso tones are reused across day/night themes without hardcoded text
  colors on input or informational surfaces.
- **Component system:** shared `Widget.ButterQueen.Button.Primary`, `Outlined`,
  `Text`, `Card` and `TextField` styles (16dp button/input corners; 20dp
  card corners; contrast-aware theme colors). Applied to customer and Bakery
  Studio XML layouts.
- **Content hierarchy:** consistent large section headings, muted supporting
  copy, breathing room between product/payment sections and 48dp minimum
  primary tap targets.
- **Responsiveness:** wrap-content UI cards where practical; no fixed-height
  admin KPI cards that cut off larger fonts, and actions can use two lines.
- **Customer guidance:** actionable cart, wishlist, order and catalog empty
  states; clear retry messaging for catalog/category network failures.
- **Robustness:** cart actions use the current RecyclerView binding position,
  not an out-of-date bind-time index; adapters and their live rating listeners
  detach with fragment views.
- **Navigation:** Back from root-level secondary customer fragments returns
  to Home; Back on Home follows Android's normal behavior; open drawer closes
  instead of exiting.
- **Performance:** live review listeners exist only on currently visible
  product cards; no new cloud services or Firebase billing required.

## Customer coverage

- Home: accessible banner contrast, search 56dp, new welcome copy, consistent
  call-to-action cards, loading and retry, category chips, empty-filter reset.
- Categories: loading, empty state and retry.
- Product details and verified product reviews: consistent buttons and
  text inputs without changing eligibility validation.
- Wishlist, Cart and Orders: shopping navigation from empty states,
  consistent cards and title hierarchy; Orders loading/error feedback.
- Checkout: 20dp outer spacing, consistent information cards, outlined fields,
  payment-method clarity. **PayHere remains sandbox.**
- Custom cake request + request history: consistent action buttons and
  outlined form fields, existing free-plan photo limitations retained.
- Profile, Settings, Help, About, Contact: unified surface/button appearance,
  preferences, app lock, local photos and intent actions preserved.
- Authentication: Sign In, Sign Up and Forgot Password inputs/cards/buttons.
- Order receipts, JSON/PDF and notifications: theme styling unchanged in
  functionality, accessibility improvements where possible.

## Admin coverage

- Bakery Studio summary: responsive metric card heights and multi-line
  quick-management actions.
- Admin product editor, inventory/order manager, sales reports, custom cake
  inbox, admin profile: Material 3 button/text-field surface harmonization.
- Sales reports screen now gives its scroll region the **remaining** height
  below the toolbar, rather than another entire window height.

## QA checklist — run in emulator AND on-device

Code inspection and CI compilation alone **cannot** establish that every
visual defect was eliminated. Record screenshots and results here.

| Check | Device / setting | Expected | Result |
|---|---|---|---|
| Light/dark mode | API 28 and API 36 | Readable contrast and themed cards | To test |
| Small screen | 320dp/360dp width | No clipped headers, charts or actions | To test |
| Large font | System font scale 1.3–1.5 | Content scrolls, buttons readable | To test |
| Landscape | Customer forms | Inputs remain reachable with keyboard | To test |
| TalkBack | Home, Checkout, Cart | Form fields and actions are announced | To test |
| Navigation | Open Categories/Orders/Settings, press Back | Returns to Home | To test |
| Drawer | Open drawer, press Back | Drawer closes | To test |
| Empty Cart | No items | Browsing CTA opens catalog | To test |
| Cart updates | Increase/decrease/remove quickly | Correct item updates (no stale index) | To test |
| Empty Wishlist | Nothing saved | Browsing CTA opens catalog | To test |
| No search match | Filter/search | Clear filters recovers results | To test |
| Offline catalog | Turn off Wi-Fi | Inline failure message and retry | To test |
| Offline orders | Turn off Wi-Fi, reopen | Readable error or cached order list | To test |
| Reviews | Open product from Home or Wishlist | Star/count update for actual reviews | To test |
| Camera/gallery | Customer profile/custom cake | No crash, selected photo visible | To test |
| Maps | Delivery areas | Map gestures and markers intact | To test |
| Biometrics | Enable, lock, unlock | Existing secure authentication intact | To test |
| Checkout | Place test COD order | Totals and Firestore order unchanged | To test |
| PayHere | Sandbox test | Still sandbox-only, no false 'paid' status | To test |
| Notifications | Custom request + admin inbox | Existing Spark in-app alerts still work | To test |

## Out of scope and caveats

This is a **broad design and interaction pass**, not a promise that every
device/rendering variant has been tested. It does not migrate payment systems,
change Firestore rules, add subscription services or move local photos.
Unreferenced legacy demo layouts are left intact to avoid unexpected Java
resource dependencies. A further per-screen device screenshot review may
still uncover pixel alignment or accessibility issues.
