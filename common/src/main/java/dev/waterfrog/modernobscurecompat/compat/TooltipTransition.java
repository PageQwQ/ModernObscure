package dev.waterfrog.modernobscurecompat.compat;

import dev.waterfrog.modernobscurecompat.config.CompatClientConfig;
import org.joml.Vector2i;
import org.joml.Vector2ic;

/**
 * Adds a silky "glide" to obscure-tooltips' tooltip placement plus a size
 * transition, mirroring ColorTooltips' lively feel.
 *
 * <p>obscure-tooltips computes the tooltip position once per frame through the
 * {@link net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner}
 * it receives, then draws the ModernUI SDF background, the frame, effects and
 * every content component relative to that position. Redirecting the positioner
 * is therefore the single choke point that moves the whole tooltip as one unit.
 *
 * <p>Position eases toward the real positioner's output with a frame-rate
 * independent exponential smoothing so the tooltip trails the cursor instead of
 * snapping. Size is expressed as a scale factor returned by
 * {@link #getScaleX()}/{@link #getScaleY()} (the size transition is applied as a
 * pose transform by the mixin), which starts below 1 when the tooltip appears
 * (pop-in) and eases back to 1, and also re-expands when the content switches to
 * a differently sized tooltip.
 */
public final class TooltipTransition {

    /**
     * Exponential approach rates (per second). At 16.0 a frame covers roughly a
     * quarter of the remaining distance, matching ColorTooltips' default.
     */
    private static final float BASE_SPEED = 16.0f;
    private static final float BASE_SIZE_SPEED = 14.0f;
    private static final float MAX_DT = 0.05f;
    private static final float SNAP_EPSILON = 0.5f;
    private static final float SCALE_EPSILON = 0.001f;
    /** A gap longer than this means the tooltip was gone; the next one snaps. */
    private static final long STALE_MS = 100L;

    private static float currentX;
    private static float currentY;
    private static float displayW;
    private static float displayH;
    private static float scaleX = 1.0f;
    private static float scaleY = 1.0f;
    private static long lastCallMs;
    private static boolean active;

    private TooltipTransition() {
    }

    /**
     * Advances the glide/size transition toward the positioner's target for this
     * frame and returns the position the tooltip should actually be drawn at.
     * Call {@link #getScaleX()}/{@link #getScaleY()} right after to obtain the
     * matching size transition.
     */
    public static synchronized Vector2ic animate(int targetX, int targetY,
                                                  int targetWidth, int targetHeight) {
        float tx = targetX;
        float ty = targetY;
        long now = System.currentTimeMillis();
        long elapsed = now - lastCallMs;

        boolean enabled = CompatClientConfig.isEnabled();
        boolean newAppearance = !active || lastCallMs == 0L || elapsed > STALE_MS;
        // A second tooltip drawn within the same millisecond must not inherit the
        // first one's animation state.
        boolean sameFrame = now == lastCallMs;
        boolean smoothMove = enabled && CompatClientConfig.isSmoothMovement() && !sameFrame;
        boolean smoothSize = enabled && CompatClientConfig.isSmoothSize() && !sameFrame;

        if (!smoothMove) {
            currentX = tx;
            currentY = ty;
        } else if (newAppearance) {
            currentX = tx;
            currentY = ty;
        } else {
            float distance = (float) Math.sqrt(
                    (tx - currentX) * (tx - currentX) + (ty - currentY) * (ty - currentY));
            if (distance > CompatClientConfig.getSnapDistance()) {
                currentX = tx;
                currentY = ty;
            } else {
                float dt = dt(elapsed);
                float speed = BASE_SPEED * (float) CompatClientConfig.getMovementSpeed();
                float blend = 1.0f - (float) Math.exp(-speed * dt);
                currentX += (tx - currentX) * blend;
                currentY += (ty - currentY) * blend;
                if (Math.abs(tx - currentX) < SNAP_EPSILON) {
                    currentX = tx;
                }
                if (Math.abs(ty - currentY) < SNAP_EPSILON) {
                    currentY = ty;
                }
            }
        }

        if (!smoothSize) {
            displayW = targetWidth;
            displayH = targetHeight;
        } else if (newAppearance) {
            // Pop-in: start smaller and grow to the real size.
            float start = (float) CompatClientConfig.getStartScale();
            displayW = targetWidth * start;
            displayH = targetHeight * start;
        } else {
            float dt = dt(elapsed);
            float sizeSpeed = BASE_SIZE_SPEED * (float) CompatClientConfig.getSizeSpeed();
            displayW = approach(displayW, targetWidth, sizeSpeed, dt);
            displayH = approach(displayH, targetHeight, sizeSpeed, dt);
        }

        scaleX = targetWidth > 0 ? displayW / targetWidth : 1.0f;
        scaleY = targetHeight > 0 ? displayH / targetHeight : 1.0f;
        if (!smoothSize) {
            scaleX = 1.0f;
            scaleY = 1.0f;
        }

        lastCallMs = now;
        active = true;
        return new Vector2i(Math.round(currentX), Math.round(currentY));
    }

    /** Horizontal size scale, 1.0 = target width. */
    public static synchronized float getScaleX() {
        return scaleX;
    }

    /** Vertical size scale, 1.0 = target height. */
    public static synchronized float getScaleY() {
        return scaleY;
    }

    private static float dt(long elapsed) {
        return Math.min(MAX_DT, Math.max(0.0f, elapsed / 1000.0f));
    }

    private static float approach(float current, float target, float speed, float dt) {
        float diff = target - current;
        if (Math.abs(diff) < SCALE_EPSILON) {
            return target;
        }
        float blend = 1.0f - (float) Math.exp(-speed * dt);
        return current + diff * blend;
    }
}
