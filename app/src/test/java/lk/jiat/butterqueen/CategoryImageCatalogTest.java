package lk.jiat.butterqueen;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import lk.jiat.butterqueen.data.CategoryImageCatalog;

public class CategoryImageCatalogTest {

    private void expectPhoto(String category, String photoId) {
        String url = CategoryImageCatalog.recommendedUrl(category);
        assertTrue("Missing photo for " + category, url != null);
        assertTrue("Unexpected photo for " + category, url.contains("/" + photoId + "/"));
        assertTrue("Expected HTTPS Pexels photo", url.startsWith("https://images.pexels.com/"));
    }

    @Test
    public void picksCuratedPhotosForBakeryCategories() {
        expectPhoto("Cakes", "7552571");
        expectPhoto("Cupcakes", "853004");
        expectPhoto("Doughnuts", "7034526");
        expectPhoto("Donuts", "7034526");
        expectPhoto("Wedding Cakes", "29040963");
        expectPhoto("Brownies", "5386666");
        expectPhoto("Cheesecakes", "13020801");
        expectPhoto("Cookies", "34591953");
        expectPhoto("Macarons", "8356243");
        expectPhoto("Pastries", "2245293");
    }

    @Test
    public void specificCakeCategoriesTakePriorityOverGenericCakes() {
        expectPhoto("  wedding CAKES  ", "29040963");
        expectPhoto("Cheese Cake", "13020801");
        expectPhoto("Mini Cupcakes", "853004");
    }

    @Test
    public void unknownCategoriesDoNotGetIncorrectStockPhotos() {
        assertNull(CategoryImageCatalog.recommendedUrl("Gift Boxes"));
        assertNull(CategoryImageCatalog.recommendedUrl(""));
        assertNull(CategoryImageCatalog.recommendedUrl(null));
    }
}
