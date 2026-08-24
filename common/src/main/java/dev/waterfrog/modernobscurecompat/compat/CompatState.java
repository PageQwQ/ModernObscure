package dev.waterfrog.modernobscurecompat.compat;

/**
 * Thread-local state shared between compat mixins without polluting target classes.
 */
public final class CompatState {

    private CompatState() {}

    private static final ThreadLocal<Boolean> RENDERING = ThreadLocal.withInitial(() -> false);

    /**
     * Active while the ModernUI SDF background is on screen until the final
     * tooltip content flush. Any batch drawn inside this window (mid-loop
     * renderItem/blit flushes from ANY mod's tooltip components) must draw
     * with clean GL state, because glyph/image render types re-apply their
     * own depth/blend state at draw time and read fog uniforms from
     * RenderSystem statics.
     */
    private static final ThreadLocal<Boolean> CONTENT_WINDOW = ThreadLocal.withInitial(() -> false);

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

    public static boolean isInContentWindow() {
        return CONTENT_WINDOW.get();
    }

    public static void openContentWindow() {
        CONTENT_WINDOW.set(true);
    }

    public static void closeContentWindow() {
        if (CONTENT_WINDOW.get()) {
            CONTENT_WINDOW.remove();
        }
    }
}
