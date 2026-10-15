package lk.jiat.butterqueen.data;

import java.util.Locale;

/**
 * Review average is computed from the product's verified review documents,
 * never from a manually entered product.rating field.
 */
public final class ProductRatingSummary {
    private int count;
    private int starsTotal;

    public void add(Long stars) {
        if (stars == null || stars < 1 || stars > 5) return;
        count++;
        starsTotal += stars.intValue();
    }

    public int getCount() { return count; }

    public double getAverage() {
        return count == 0 ? 0.0 : (double) starsTotal / count;
    }

    public static String emptyLabel() {
        return "★ — / 5  •  0 reviews";
    }

    public String label() {
        if (count == 0) return emptyLabel();
        return String.format(Locale.getDefault(), "★ %.1f / 5  •  %d review%s",
                getAverage(), count, count == 1 ? "" : "s");
    }
}
