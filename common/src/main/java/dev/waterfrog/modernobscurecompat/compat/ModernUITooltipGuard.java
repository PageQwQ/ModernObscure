package dev.waterfrog.modernobscurecompat.compat;

import java.lang.reflect.Field;

/**
 * Temporarily overrides ModernUI's "modern tooltip" option (icyllis' {@code sTooltip})
 * while obscure-tooltips renders its own tooltip, and restores the user's original
 * setting afterwards.
 *
 * <p>ModernUI's own renderTooltipInternal HEAD handler draws its tooltip and cancels
 * the method when {@code sTooltip} is true. Mixin handler order cannot be relied upon
 * across mods, so this guard is applied from a non-cancellable handler (which always
 * runs first at an injection point) and restored from the frame-end hook as a catch-all.
 */
public final class ModernUITooltipGuard {

    private ModernUITooltipGuard() {
    }

    private static Field sTooltipField;
    private static boolean reflectionInitAttempted;

    private static final ThreadLocal<Boolean> SAVED = new ThreadLocal<>();

    private static void initReflection() {
        if (reflectionInitAttempted) return;
        reflectionInitAttempted = true;
        try {
            Class<?> clazz = Class.forName("icyllis.modernui.mc.TooltipRenderer");
            sTooltipField = clazz.getDeclaredField("sTooltip");
            sTooltipField.setAccessible(true);
        } catch (Exception ignored) {
        }
    }

    public static boolean isAvailable() {
        initReflection();
        return sTooltipField != null;
    }

    public static boolean read() {
        initReflection();
        if (sTooltipField == null) return false;
        try {
            return sTooltipField.getBoolean(null);
        } catch (Exception e) {
            return false;
        }
    }

    public static void set(boolean value) {
        if (sTooltipField == null) return;
        try {
            sTooltipField.setBoolean(null, value);
        } catch (Exception ignored) {
        }
    }

    /**
     * Saves the user's current option value (first call wins, so re-entrant renders
     * keep the original value) and forces it off.
     */
    public static void saveAndDisable() {
        if (!isAvailable()) return;
        if (SAVED.get() == null) {
            SAVED.set(read());
        }
        set(false);
    }

    /**
     * Restores the user's option value saved by {@link #saveAndDisable()}.
     * A no-op if nothing was saved.
     */
    public static void restore() {
        if (!isAvailable()) return;
        Boolean saved = SAVED.get();
        if (saved != null) {
            set(saved);
            SAVED.remove();
        }
    }
}
