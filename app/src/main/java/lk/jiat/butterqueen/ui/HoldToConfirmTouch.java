package lk.jiat.butterqueen.ui;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;

/**
 * A continuous single-finger ACTION_DOWN -> five-second hold.
 * Releasing early, moving outside, extra pointers or lifecycle teardown
 * cancels the gesture. A successful hold only unlocks the final dialog;
 * it must never directly delete a Firebase account.
 */
public final class HoldToConfirmTouch implements View.OnTouchListener {
    private final MaterialButton button;
    private final ProgressBar progress;
    private final TextView hint;
    private final String idleLabel;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private boolean tracking;
    private boolean armed;
    private boolean disposed;
    private int pointerId = -1;
    private long startedAt;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!tracking || disposed || !button.isAttachedToWindow()) {
                cancel();
                return;
            }
            long elapsed = SystemClock.uptimeMillis() - startedAt;
            progress.setProgress(HoldToDeletePolicy.progress(elapsed));
            if (HoldToDeletePolicy.completed(elapsed)) {
                tracking = false;
                pointerId = -1;
                button.setPressed(false);
                button.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                button.setText("Hold completed");
                hint.setText("Now confirm account deletion in the dialog");
                armed = true;
                // performClick is necessary for accessibility and Android's
                // touch listener contract. The Fragment checks consumeArmed().
                button.performClick();
                return;
            }
            int remaining = HoldToDeletePolicy.remainingSeconds(elapsed);
            button.setText("Keep holding... " + remaining + "s");
            hint.setText("Do not release your finger until the countdown ends");
            handler.postDelayed(this, 50L);
        }
    };

    public HoldToConfirmTouch(MaterialButton button, ProgressBar progress,
                              TextView hint) {
        this.button = button;
        this.progress = progress;
        this.hint = hint;
        this.idleLabel = "Hold 5 seconds to delete account";
        resetVisuals();
    }

    /** True once for the internally dispatched click after a complete hold. */
    public boolean consumeArmed() {
        boolean allowed = armed;
        armed = false;
        return allowed;
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        if (disposed || !view.isEnabled()) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                cancel();
                tracking = true;
                pointerId = event.getPointerId(0);
                startedAt = SystemClock.uptimeMillis();
                button.setPressed(true);
                view.getParent().requestDisallowInterceptTouchEvent(true);
                progress.setProgress(0);
                hint.setText("Keep your finger pressed for 5 seconds");
                handler.post(tick);
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_CANCEL:
                cancel();
                return true;

            case MotionEvent.ACTION_MOVE:
                if (tracking) {
                    int index = event.findPointerIndex(pointerId);
                    if (index < 0 || !withinBounds(view, event.getX(index), event.getY(index))) {
                        cancel();
                    }
                }
                return true;

            case MotionEvent.ACTION_UP:
                // A click is never dispatched from ACTION_UP before five seconds.
                // The five-second timer alone dispatches a single performClick().
                cancel();
                return true;

            default:
                return true;
        }
    }

    private static boolean withinBounds(View view, float x, float y) {
        return x >= 0 && y >= 0 && x < view.getWidth() && y < view.getHeight();
    }

    public void cancel() {
        tracking = false;
        pointerId = -1;
        armed = false;
        handler.removeCallbacks(tick);
        button.setPressed(false);
        if (button.getParent() != null)
            button.getParent().requestDisallowInterceptTouchEvent(false);
        resetVisuals();
    }

    private void resetVisuals() {
        button.setText(idleLabel);
        progress.setProgress(0);
        hint.setText("Press and keep holding for 5 seconds. Release to cancel.");
    }

    public void dispose() {
        disposed = true;
        cancel();
        button.setOnTouchListener(null);
    }
}
