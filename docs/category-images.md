# Butter Queen category photography

These curated Pexels photographs are used **only for category cards** when
Firestore category `imageUrl` is blank. They are not photos of Butter Queen
products. Admin-uploaded category photos retain priority.

| Category | Pexels photo |
| --- | --- |
| Cakes | https://www.pexels.com/photo/white-cake-on-white-table-top-7552571/ |
| Cupcakes | https://www.pexels.com/photo/three-baked-muffin-with-strawberry-filling-on-top-853004/ |
| Doughnuts / Donuts | https://www.pexels.com/photo/photograph-of-pink-and-white-donuts-with-sprinkles-7034526/ |
| Wedding Cakes | https://www.pexels.com/photo/elegant-white-wedding-cake-with-floral-decor-29040963/ |
| Brownies | https://www.pexels.com/photo/chocolate-brownies-with-blueberries-on-white-surface-5386666/ |
| Cheesecakes | https://www.pexels.com/photo/close-up-shot-of-a-slice-of-cheesecake-13020801/ |
| Cookies | https://www.pexels.com/photo/delicious-homemade-chocolate-chip-cookies-34591953/ |
| Macarons | https://www.pexels.com/photo/french-macarons-on-the-plate-8356243/ |
| Pastries | https://www.pexels.com/photo/assorted-bread-on-beige-wooden-table-2245293/ |

License: https://www.pexels.com/legal-pages/license/

Pexels permits free commercial use and modification, including use in apps,
without required attribution; credit is appreciated. Do not imply that these
stock photos are the bakery's actual products or imply photographer endorsement.

## Implementation

- `CategoryImageCatalog.recommendedUrl(name)` maps known category names to
  size-limited HTTPS image URLs from `images.pexels.com`.
- `CategoryAdapter` loads an explicit Firestore `imageUrl` first, then any
  legacy local resource, then the curated stock photo.
- A bundled local drawable remains the placeholder and network error fallback.
- Unknown/new admin categories still display the local generic image unless a
  category photo is uploaded in Firestore.
- The Pexels URLs load using Glide; downloaded images can be cached by Glide,
  but a first-time load requires internet access.

Future production hardening: replace remote stock URLs with optimized WebP
assets owned/hosted by Butter Queen. Keep real product photos separate from
these category illustrations. New categories must be added through the
existing Firebase admin flow; this change does not create categories.
