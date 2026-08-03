package dev.waterfrog.modernobscurecompat.compat;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.obscuria.tooltips.client.TooltipState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;

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

    // Reflection handle for Matrix4fStack.current to save/restore stack depth
    private static Field modelViewStackCurrentField;

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

            // For saving/restoring ModelViewStack depth
            modelViewStackCurrentField = Matrix4fStack.class.getDeclaredField("current");
            modelViewStackCurrentField.setAccessible(true);
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
            return;
        }

        try {
            // --- Get UIManager instance and its TooltipRenderer ---
            Object uiManager = uiManagerGetInstanceMethod.invoke(null);
            Field trField = uiManagerClass.getDeclaredField("mTooltipRenderer");
            trField.setAccessible(true);
            Object tooltipRenderer = trField.get(uiManager);
            if (tooltipRenderer == null) {
                return;
            }

            // --- Call computeWorkingColor ---
            Method computeWorkingColor = tooltipRendererClass.getDeclaredMethod("computeWorkingColor", net.minecraft.world.item.ItemStack.class);
            computeWorkingColor.setAccessible(true);
            computeWorkingColor.invoke(tooltipRenderer, state.stack);

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

            // Save ModelViewStack depth to restore in case the method throws
            // and leaves the stack unbalanced (which causes "max stack size of 16" crashes).
            Matrix4fStack modelViewStack = RenderSystem.getModelViewStack();
            int originalDepth = 0;
            if (modelViewStackCurrentField != null) {
                originalDepth = modelViewStackCurrentField.getInt(modelViewStack);
            }

            try {
                // Delegate to ModernUI's own rendering method.
                // It handles shader setup, blending, vertex format, and buffering correctly.
                // Params: GuiGraphics, Matrix4f, x, y, width, height, useGradient, zLevel
                drawRoundedBgMethod.invoke(tooltipRenderer,
                        gr, pose, x, y, contentWidth, contentHeight, false, 0);
            } finally {
                // Restore ModelViewStack depth. ModernUI's method pushes/pops internally,
                // and if it throws, the stack is left unbalanced. This ensures balance.
                if (modelViewStackCurrentField != null) {
                    int currentDepth = modelViewStackCurrentField.getInt(modelViewStack);
                    int diff = currentDepth - originalDepth;
                    if (diff > 0) {
                        for (int i = 0; i < diff; i++) {
                            modelViewStack.popMatrix();
                        }
                        RenderSystem.applyModelViewMatrix();
                    }
                }
            }
        } catch (Exception ignored) {
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