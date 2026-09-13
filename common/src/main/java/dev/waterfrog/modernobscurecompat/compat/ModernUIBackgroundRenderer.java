package dev.waterfrog.modernobscurecompat.compat;

import dev.obscuria.tooltips.client.TooltipState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Renders ModernUI-style rounded SDF tooltip backgrounds for obscure-tooltips.
 *
 * <p>All ModernUI access goes through reflection so the mod has no compile-time
 * or hard runtime dependency on ModernUI; when it is absent the caller falls
 * back to obscure-tooltips' own panel.
 *
 * <p>In 1.21.11 ModernUI's {@code TooltipRenderer#drawRoundedBackground} is a
 * private instance method that computes its own uniforms and submits a
 * {@code GradientRectangleRenderState} through the new GUI render-state system,
 * so this class only has to prime the working color / border animation and pass
 * the current pose and scissor rectangle.
 *
 * <p>ModernUI binds the {@code ModernTooltip} uniform buffer in
 * {@code GuiRenderer.executeDrawRange} only while {@code TooltipRenderer.sTooltip}
 * is true. The tooltip guard restores that option as soon as obscure-tooltips has
 * submitted its draw, so the background's uniforms are bound for the frame.
 */
public final class ModernUIBackgroundRenderer {

    private ModernUIBackgroundRenderer() {
    }

    private static Class<?> tooltipRendererClass;
    private static Class<?> uiManagerClass;
    private static Class<?> muiModApiClass;

    private static Field mTooltipRendererField;
    private static Field sBorderColorCycleField;

    private static Method uiManagerGetInstanceMethod;
    private static Method computeWorkingColorMethod;
    private static Method updateBorderColorMethod;
    private static Method drawRoundedBackgroundMethod;
    private static Method muiModApiGetMethod;
    private static Method peekScissorStackMethod;

    private static boolean reflectionInitialized;
    private static boolean reflectionAvailable;

    private static synchronized void initReflection() {
        if (reflectionInitialized) return;
        reflectionInitialized = true;
        try {
            tooltipRendererClass = Class.forName("icyllis.modernui.mc.TooltipRenderer");
            uiManagerClass = Class.forName("icyllis.modernui.mc.UIManager");
            muiModApiClass = Class.forName("icyllis.modernui.mc.MuiModApi");

            mTooltipRendererField = uiManagerClass.getField("mTooltipRenderer");
            sBorderColorCycleField = tooltipRendererClass.getField("sBorderColorCycle");

            uiManagerGetInstanceMethod = uiManagerClass.getMethod("getInstance");
            computeWorkingColorMethod = tooltipRendererClass.getDeclaredMethod("computeWorkingColor", ItemStack.class);
            computeWorkingColorMethod.setAccessible(true);
            updateBorderColorMethod = tooltipRendererClass.getDeclaredMethod("updateBorderColor");
            updateBorderColorMethod.setAccessible(true);
            drawRoundedBackgroundMethod = tooltipRendererClass.getDeclaredMethod(
                    "drawRoundedBackground",
                    GuiGraphics.class,
                    Matrix3x2f.class,
                    ScreenRectangle.class,
                    float.class, float.class, int.class, int.class,
                    boolean.class, int.class);
            drawRoundedBackgroundMethod.setAccessible(true);

            muiModApiGetMethod = muiModApiClass.getMethod("get");
            peekScissorStackMethod = muiModApiClass.getMethod("peekScissorStack", GuiGraphics.class);

            reflectionAvailable = true;
        } catch (Exception ignored) {
            reflectionAvailable = false;
        }
    }

    /**
     * Draws the ModernUI rounded background for an obscure-tooltips panel.
     *
     * @return {@code true} if ModernUI handled the draw; {@code false} if the
     *         caller should fall back to obscure-tooltips' own panel.
     */
    public static boolean drawRoundedBackground(GuiGraphics graphics,
                                                float x, float y,
                                                int contentWidth, int contentHeight,
                                                TooltipState state) {
        initReflection();
        if (!reflectionAvailable) {
            return false;
        }
        try {
            Object uiManager = uiManagerGetInstanceMethod.invoke(null);
            if (uiManager == null) {
                return false;
            }
            Object tooltipRenderer = mTooltipRendererField.get(uiManager);
            if (tooltipRenderer == null) {
                return false;
            }

            computeWorkingColorMethod.invoke(tooltipRenderer, state.stack);
            if (sBorderColorCycleField.getInt(null) > 0) {
                updateBorderColorMethod.invoke(tooltipRenderer);
            }

            Object modApi = muiModApiGetMethod.invoke(null);
            // ModernUI accepts a null scissor (no active scissor rectangle), so
            // pass whatever the scissor stack currently holds.
            Object scissor = peekScissorStackMethod.invoke(modApi, graphics);

            Matrix3x2f pose = new Matrix3x2f(graphics.pose());
            drawRoundedBackgroundMethod.invoke(tooltipRenderer, graphics, pose, scissor,
                    x, y, contentWidth, contentHeight, false, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
