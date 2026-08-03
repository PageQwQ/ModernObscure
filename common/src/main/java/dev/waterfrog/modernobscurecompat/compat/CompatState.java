package dev.waterfrog.modernobscurecompat.compat;

/**
 * Thread-local state shared between compat mixins without polluting target classes.
 */
public final class CompatState {

    private CompatState() {}

    private static final ThreadLocal<Boolean> RENDERING = ThreadLocal.withInitial(() -> false);

    public static boolean isRendering() {
        return RENDERING.get();
    }

    public static void setRendering(boolean value) {
        if (value) {
            RENDERING.set(true);
        } else {
            RENDERING.remove();
        }
    }
}
