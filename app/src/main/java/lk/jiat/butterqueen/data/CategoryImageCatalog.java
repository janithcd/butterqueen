package lk.jiat.butterqueen.data;

import java.util.Locale;

import lk.jiat.butterqueen.R;

/**
 * Curated Pexels category artwork. This is only used when a Firestore category
 * does not have an imageUrl and no legacy local image has been assigned.
 *
 * Actual bakery products should keep their own photos, never these stock images.
 * See docs/category-images.md for the source pages and licensing information.
 */
public final class CategoryImageCatalog {

    private CategoryImageCatalog() {
    }

    public static String recommendedUrl(String categoryName) {
        String name = normalize(categoryName);
        if (name.isEmpty()) return null;

        // Most specific categories first: "wedding cakes" must not use "cakes".
        if (name.contains("wedding")) return pexelsPhoto(29040963);
        if (name.contains("cheesecake") || name.contains("cheese cake")) {
            return pexelsPhoto(13020801);
        }
        if (name.contains("cupcake")) return pexelsPhoto(853004);
        if (name.contains("brownie")) return pexelsPhoto(5386666);
        if (name.contains("donut") || name.contains("doughnut")) {
            return pexelsPhoto(7034526);
        }
        if (name.contains("cookie") || name.contains("biscuit")) {
            return pexelsPhoto(34591953);
        }
        if (name.contains("macaron")) return pexelsPhoto(8356243);
        if (name.contains("pastr") || name.contains("croissant")) {
            return pexelsPhoto(2245293);
        }
        if (name.contains("cake")) return pexelsPhoto(7552571);

        // Unknown/admin-created categories keep the existing local placeholder
        // until an administrator uploads a suitable category image.
        return null;
    }

    public static int fallbackDrawable(String categoryName) {
        String name = normalize(categoryName);
        if (name.contains("cupcake")) return R.drawable.cupcake;
        if (name.contains("brownie")) return R.drawable.choc_cake;
        if (name.contains("donut") || name.contains("doughnut")) {
            return R.drawable.donut;
        }
        if (name.contains("wedding") || name.contains("birthday")) {
            return R.drawable.birthday;
        }
        return R.drawable.cake_wallpaper;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String pexelsPhoto(int photoId) {
        return "https://images.pexels.com/photos/" + photoId
                + "/pexels-photo-" + photoId
                + ".jpeg?auto=compress&cs=tinysrgb&w=600";
    }
}
