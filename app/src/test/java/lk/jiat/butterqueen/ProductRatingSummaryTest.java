package lk.jiat.butterqueen;

import org.junit.Test;
import static org.junit.Assert.*;

import lk.jiat.butterqueen.data.ProductRatingSummary;

public class ProductRatingSummaryTest {

    @Test
    public void emptyProductDisplaysZeroReviewsWithoutInventedRating() {
        ProductRatingSummary summary = new ProductRatingSummary();
        assertEquals(0, summary.getCount());
        assertEquals(0.0, summary.getAverage(), 0.0001);
        assertTrue(summary.label().contains("0 reviews"));
        assertTrue(summary.label().contains("—"));
    }

    @Test
    public void averageAndCountUseActualStars() {
        ProductRatingSummary summary = new ProductRatingSummary();
        summary.add(5L);
        summary.add(4L);
        summary.add(3L);
        assertEquals(3, summary.getCount());
        assertEquals(4.0, summary.getAverage(), 0.0001);
        assertTrue(summary.label().contains("3 reviews"));
    }

    @Test
    public void invalidOrMissingStarsAreExcluded() {
        ProductRatingSummary summary = new ProductRatingSummary();
        summary.add(null);
        summary.add(0L);
        summary.add(6L);
        summary.add(5L);
        assertEquals(1, summary.getCount());
        assertEquals(5.0, summary.getAverage(), 0.0001);
        assertTrue(summary.label().contains("1 review"));
        assertFalse(summary.label().contains("1 reviews"));
    }
}
