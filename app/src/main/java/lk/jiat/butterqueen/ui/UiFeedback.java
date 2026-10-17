package lk.jiat.butterqueen.ui;

import android.graphics.drawable.GradientDrawable;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;

import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;

/** Theme-aware floating messages and tactile button feedback. */
public final class UiFeedback {
    private UiFeedback() {}

    public static void success(View anchor, String message) {
        show(anchor, "✓  " + message, false);
    }

    public static void error(View anchor, String message) {
        show(anchor, "!  " + message, true);
    }

    private static void show(View anchor, String message, boolean error) {
        if (anchor == null || !anchor.isAttachedToWindow()) return;
        Snackbar snackbar = Snackbar.make(anchor, message,
                error ? Snackbar.LENGTH_LONG : Snackbar.LENGTH_SHORT);
        int color = ContextCompat.getColor(anchor.getContext(), error
                ? lk.jiat.butterqueen.R.color.md_theme_errorContainer
                : lk.jiat.butterqueen.R.color.md_theme_primaryContainer);
        int onColor = ContextCompat.getColor(anchor.getContext(), error
                ? lk.jiat.butterqueen.R.color.md_theme_onErrorContainer
                : lk.jiat.butterqueen.R.color.md_theme_onPrimaryContainer);
        snackbar.setTextColor(onColor);
        GradientDrawable background = new GradientDrawable();
        background.setColor(color);
        float density = anchor.getResources().getDisplayMetrics().density;
        background.setCornerRadius(18 * density);
        View bar = snackbar.getView();
        bar.setBackground(background);
        bar.setElevation(8 * density);
        ViewGroup.LayoutParams params = bar.getLayoutParams();
        if (params instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams margins = (ViewGroup.MarginLayoutParams) params;
            margins.leftMargin = (int) (14 * density);
            margins.rightMargin = (int) (14 * density);
            margins.bottomMargin += (int) (8 * density);
            bar.setLayoutParams(margins);
        }
        snackbar.show();
    }

    public static void press(View view) {
        if (view == null) return;
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        view.animate().cancel();
        view.setScaleX(1f);
        view.setScaleY(1f);
        view.animate().scaleX(0.96f).scaleY(0.96f).setDuration(85)
                .withEndAction(() -> view.animate().scaleX(1f)
                        .scaleY(1f).setDuration(145).start()).start();
    }
}
