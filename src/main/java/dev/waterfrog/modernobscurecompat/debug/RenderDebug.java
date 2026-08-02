package dev.waterfrog.modernobscurecompat.debug;

/**
 * Simple debug logger for tooltip render layer tracking.
 * Each call records a timestamped step so the user can see
 * the exact render order in the log.
 */
public final class RenderDebug {

    private RenderDebug() {}

    private static final boolean ENABLED = true;

    private static long startTime;

    public static void startFrame(String label) {
        if (!ENABLED) return;
        startTime = System.currentTimeMillis();
        System.out.println("[RenderDebug] ====== " + label + " ======");
    }

    public static void step(String layer, String action) {
        if (!ENABLED) return;
        long elapsed = startTime > 0 ? System.currentTimeMillis() - startTime : 0;
        System.out.println("[RenderDebug] [t+" + elapsed + "ms] LAYER=" + layer + " ACTION=" + action);
    }
}
