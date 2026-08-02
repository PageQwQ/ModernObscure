package dev.waterfrog.modernobscurecompat.compat;

import dev.waterfrog.modernobscurecompat.debug.RenderDebug;
import dev.obscuria.tooltips.client.TooltipState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Renders ModernUI-style rounded SDF tooltip backgrounds.
 * All ModernUI access is through reflection so no compile-time dependency.
 */
public final class ModernUIBackgroundRenderer {

    private ModernUIBackgroundRenderer() {
    }

    // ---- Cached reflection handles ----
    private static Class<?> tooltipRendererClass;
    private static Class<?> uiManagerClass;
    private static Class<?> guiRenderTypeClass;

    private static Field sShadowRadiusField;
    private static Field sShadowAlphaField;
    private static Field sFillColorField;
    private static Field sCornerRadiusField;
    private static Field sBorderWidthField;
    private static Field sBorderColorCycleField;
    private static Field hBorderField;
    private static Field vBorderField;

    private static Method getShaderTooltipMethod;
    private static Method tooltipRenderTypeMethod;
    private static Method uiManagerGetInstanceMethod;
    private static Method drawRoundedBgMethod;

    private static boolean reflectionInitialized;

    // No cached RenderType — we use immediate mode drawing to bypass
    // MultiBufferSource's HashMap-based buffer ordering entirely.

    private static void initReflection() {
        if (reflectionInitialized) return;
        reflectionInitialized = true;
        try {
            tooltipRendererClass = Class.forName("icyllis.modernui.mc.TooltipRenderer");
            uiManagerClass = Class.forName("icyllis.modernui.mc.UIManager");
            guiRenderTypeClass = Class.forName("icyllis.modernui.mc.GuiRenderType");

            sShadowRadiusField = tooltipRendererClass.getDeclaredField("sShadowRadius");
            sShadowAlphaField = tooltipRendererClass.getDeclaredField("sShadowAlpha");
            sFillColorField = tooltipRendererClass.getDeclaredField("sFillColor");
            sCornerRadiusField = tooltipRendererClass.getDeclaredField("sCornerRadius");
            sBorderWidthField = tooltipRendererClass.getDeclaredField("sBorderWidth");
            sBorderColorCycleField = tooltipRendererClass.getDeclaredField("sBorderColorCycle");
            hBorderField = tooltipRendererClass.getDeclaredField("H_BORDER");
            vBorderField = tooltipRendererClass.getDeclaredField("V_BORDER");

            getShaderTooltipMethod = guiRenderTypeClass.getDeclaredMethod("getShaderTooltip");
            tooltipRenderTypeMethod = guiRenderTypeClass.getDeclaredMethod("tooltip");
            uiManagerGetInstanceMethod = uiManagerClass.getDeclaredMethod("getInstance");

            // ModernUI's own drawRoundedBackground: (GuiGraphics, Matrix4f, float, float, int, int, boolean, int)
            drawRoundedBgMethod = tooltipRendererClass.getDeclaredMethod(
                    "drawRoundedBackground",
                    net.minecraft.client.gui.GuiGraphics.class,
                    org.joml.Matrix4f.class,
                    float.class, float.class, int.class, int.class,
                    boolean.class, int.class);
            drawRoundedBgMethod.setAccessible(true);
        } catch (Exception ignored) {
        }
    }

    /**
     * Called via reflection from {@code MixinObscureTooltipRenderer}.
     */
    @SuppressWarnings("unchecked")
    public static void drawRoundedBackground(
            GuiGraphics gr, Matrix4f pose,
            float x, float y, int contentWidth, int contentHeight,
            TooltipState state) {

        initReflection();
        if (tooltipRendererClass == null) {
            RenderDebug.step("BG-SDF", "tooltipRendererClass null, abort");
            return;
        }

        try {
            RenderDebug.step("BG-SDF", "start drawRoundedBackground — dst=(" + x + "," + y + ") content=" + contentWidth + "x" + contentHeight);
            // --- Get UIManager instance and its TooltipRenderer ---
            Object uiManager = uiManagerGetInstanceMethod.invoke(null);
            Field trField = uiManagerClass.getDeclaredField("mTooltipRenderer");
            trField.setAccessible(true);
            Object tooltipRenderer = trField.get(uiManager);
            if (tooltipRenderer == null) {
                RenderDebug.step("BG-SDF", "tooltipRenderer null, abort");
                return;
            }

            // --- Call computeWorkingColor ---
            Method computeWorkingColor = tooltipRendererClass.getDeclaredMethod("computeWorkingColor", net.minecraft.world.item.ItemStack.class);
            computeWorkingColor.setAccessible(true);
            computeWorkingColor.invoke(tooltipRenderer, state.stack);
            RenderDebug.step("BG-SDF", "computeWorkingColor done");

            // --- Call updateBorderColor if cycling ---
            int borderColorCycle = sBorderColorCycleField.getInt(null);
            Field useSpectrumField = tooltipRendererClass.getDeclaredField("mUseSpectrum");
            useSpectrumField.setAccessible(true);
            Field layoutRTLField = tooltipRendererClass.getDeclaredField("mLayoutRTL");
            layoutRTLField.setAccessible(true);
            Field currTimeMillisField = tooltipRendererClass.getDeclaredField("mCurrTimeMillis");
            currTimeMillisField.setAccessible(true);

            if (borderColorCycle > 0) {
                Method updateBorderColor = tooltipRendererClass.getDeclaredMethod("updateBorderColor");
                updateBorderColor.setAccessible(true);
                updateBorderColor.invoke(tooltipRenderer);
                RenderDebug.step("BG-SDF", "updateBorderColor done");
            }

            // --- Read static config ---
            float hBorder = hBorderField.getInt(null);
            float vBorder = vBorderField.getInt(null);
            float shadowRadius = Math.max(sShadowRadiusField.getFloat(null), 0.00001f);
            float cornerRadius = sCornerRadiusField.getFloat(null);
            float borderWidth = sBorderWidthField.getFloat(null);
            float shadowAlpha = sShadowAlphaField.getFloat(null);
            int[] fillColor = (int[]) sFillColorField.get(null);

            float tooltipWidth = contentWidth;
            float tooltipHeight = contentHeight;
            float halfWidth = tooltipWidth / 2f;
            float halfHeight = tooltipHeight / 2f;
            float centerX = x + halfWidth;
            float centerY = y + halfHeight;
            float sizeX = halfWidth + hBorder;
            float sizeY = halfHeight + vBorder;

            ShaderInstance shader = (ShaderInstance) getShaderTooltipMethod.invoke(null);
            if (shader == null) {
                RenderDebug.step("BG-SDF", "shader null, abort");
                return;
            }

            shader.safeGetUniform("u_PushData0")
                    .set(sizeX, sizeY, cornerRadius, borderWidth / 2f);

            boolean useSpectrum = useSpectrumField.getBoolean(tooltipRenderer);
            boolean layoutRTL = layoutRTLField.getBoolean(tooltipRenderer);
            long currTimeMillis = currTimeMillisField.getLong(tooltipRenderer);

            float rainbowOffset = 0;
            if (useSpectrum) {
                rainbowOffset = 1;
                if (borderColorCycle > 0) {
                    long overallCycle = borderColorCycle * 4L;
                    rainbowOffset += (float) (currTimeMillis % overallCycle) / overallCycle;
                }
                if (!layoutRTL) {
                    rainbowOffset = -rainbowOffset;
                }
            }

            shader.safeGetUniform("u_PushData1")
                    .set(shadowAlpha, 1.25f / shadowRadius, (fillColor[0] >>> 24) / 255f, rainbowOffset);

            if (rainbowOffset == 0) {
                Method chooseBorderColor = tooltipRendererClass.getDeclaredMethod("chooseBorderColor", int.class);
                chooseBorderColor.setAccessible(true);
                setBorderColor(shader, "u_PushData2", (int) chooseBorderColor.invoke(tooltipRenderer, 0));
                setBorderColor(shader, "u_PushData3", (int) chooseBorderColor.invoke(tooltipRenderer, 1));
                setBorderColor(shader, "u_PushData4", (int) chooseBorderColor.invoke(tooltipRenderer, 3));
                setBorderColor(shader, "u_PushData5", (int) chooseBorderColor.invoke(tooltipRenderer, 2));
            }
            RenderDebug.step("BG-SDF", "state ready — calling ModernUI drawRoundedBackground");

            // Delegate to ModernUI's own rendering method.
            // It handles shader setup, blending, vertex format, and buffering correctly.
            // Params: GuiGraphics, Matrix4f, x, y, width, height, useGradient, zLevel
            drawRoundedBgMethod.invoke(tooltipRenderer,
                    gr, pose, x, y, contentWidth, contentHeight, false, 0);
            RenderDebug.step("BG-SDF", "ModernUI drawRoundedBackground complete");
        } catch (Exception e) {
            RenderDebug.step("BG-SDF", "EXCEPTION: " + e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private static void setBorderColor(ShaderInstance shader, String name, int argb) {
        int a = (argb >>> 24);
        int r = ((argb >> 16) & 0xff);
        int g = ((argb >> 8) & 0xff);
        int b = (argb & 0xff);
        shader.safeGetUniform(name).set(r / 255f, g / 255f, b / 255f, a / 255f);
    }
}
