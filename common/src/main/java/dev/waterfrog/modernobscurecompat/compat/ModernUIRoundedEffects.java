package dev.waterfrog.modernobscurecompat.compat;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.obscuria.fragmentum.api.common.color.ARGB;
import dev.obscuria.tooltips.client.TooltipState;
import dev.obscuria.tooltips.client.tooltip.element.QuadPalette;
import dev.obscuria.tooltips.client.tooltip.element.effect.RimLightEffect;
import dev.obscuria.tooltips.client.tooltip.element.effect.ShimmerEffect;
import dev.obscuria.tooltips.client.tooltip.element.effect.TooltipEffect;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import org.joml.Matrix4f;

import java.lang.reflect.Field;

/**
 * Redraws obscure-tooltips' rectangular "panel glow" effects around ModernUI's
 * rounded SDF tooltip background.
 *
 * <p>When ModernUI draws the tooltip, the visible panel is a rounded rectangle
 * whose outline sits at the layout box inflated by {@code TooltipRenderer.H_BORDER}
 * (4 px) with a corner radius of {@code sCornerRadius}. All of obscure-tooltips'
 * back effects, however, are hardcoded rectangles derived from the layout box
 * inflated by 3 px:
 *
 * <ul>
 *   <li>{@link RimLightEffect} strokes a rectangular ring between that rectangle
 *       and the same rectangle inset by a pulsing {@code offset};</li>
 *   <li>{@link ShimmerEffect} fills a rectangular band between that rectangle and a
 *       control rectangle inset by 12 px, modulating the inner edge with a
 *       travelling brightness wave.</li>
 * </ul>
 *
 * <p>Both therefore keep square corners and poke past ModernUI's rounded border.
 * This renderer replays each effect's <em>exact</em> maths — same palettes, same
 * easing, same vertex colours, same {@link RenderType#guiOverlay()} and therefore
 * the same blend/depth state — but walks a rounded outline instead of a rectangular
 * one. The outline radius is derived from ModernUI's own {@code sCornerRadius}, so
 * the glow stays concentric with the border whatever corner radius the user
 * configured, and no resource pack or obscure-tooltips change is involved.
 *
 * <p>Returns {@code false} for every effect it does not handle (or when ModernUI is
 * absent / anything throws), so the caller can fall back to the original
 * rectangular draw.
 */
public final class ModernUIRoundedEffects {

    /** ModernUI draws the tooltip panel at the layout box inflated by this many pixels. */
    private static final float PANEL_INSET = 4.0F;

    /**
     * obscure-tooltips draws every back effect at the layout box inflated by this
     * many pixels, i.e. 1 px inside ModernUI's panel outline.
     */
    private static final float EFFECT_OUTSET = 3.0F;

    /** {@code ShimmerEffect} lerps toward a control rectangle inset by this much. */
    private static final float SHIMMER_CONTROL_INSET = 12.0F;

    /** Target edge length of one perimeter segment, in pixels. */
    private static final float SEGMENT_LENGTH = 3.0F;

    private static final int MIN_SEGMENTS = 48;
    private static final int MAX_SEGMENTS = 400;

    private ModernUIRoundedEffects() {
    }

    // ---- Cached reflection handles ----
    private static boolean initAttempted;
    private static Field cornerRadiusField;

    private static boolean init() {
        if (!initAttempted) {
            initAttempted = true;
            try {
                cornerRadiusField = Class.forName("icyllis.modernui.mc.TooltipRenderer")
                        .getField("sCornerRadius");
            } catch (Throwable ignored) {
                cornerRadiusField = null;
            }
        }
        return cornerRadiusField != null;
    }

    /**
     * @return {@code true} when the effect was drawn with rounded geometry and the
     *         caller must skip the original rectangular draw.
     */
    public static boolean tryRender(TooltipEffect effect, TooltipState state, GuiGraphics graphics,
                                    int x, int y, int width, int height) {
        if (!init()) {
            return false;
        }
        try {
            if (effect instanceof RimLightEffect rim) {
                return renderRimLight(rim, state, graphics, x, y, width, height);
            }
            if (effect instanceof ShimmerEffect shimmer) {
                return renderShimmer(shimmer, state, graphics, x, y, width, height);
            }
        } catch (Throwable ignored) {
            // Fall through to obscure-tooltips' own draw.
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Effects
    // ------------------------------------------------------------------

    /**
     * {@code RimLightEffect.renderBack}: a ring whose outer edge is the 3 px outset
     * rectangle and whose inner edge is that rectangle inset by
     * {@code min(w, h) * 0.25 * (0.8 + 0.4 * cos(t))}, coloured by the outer / inner
     * {@link QuadPalette} pair.
     */
    private static boolean renderRimLight(RimLightEffect rim, TooltipState state, GuiGraphics graphics,
                                          int x, int y, int width, int height) {
        QuadPalette outer = rim.outerPalette();
        QuadPalette inner = rim.innerPalette();
        float time = state.timeInSeconds();

        float left = x - EFFECT_OUTSET;
        float top = y - EFFECT_OUTSET;
        float fullWidth = width + 2.0F * EFFECT_OUTSET;
        float fullHeight = height + 2.0F * EFFECT_OUTSET;
        float halfWidth = fullWidth * 0.5F;
        float halfHeight = fullHeight * 0.5F;
        float centerX = left + halfWidth;
        float centerY = top + halfHeight;

        float radius = effectRadius(halfWidth, halfHeight);

        // Same pulse as RimLightEffect: offset = min(pWidth, pHeight) * 0.25 * scale.
        float scale = 0.8F + 0.4F * (float) Math.cos(time);
        float band = Math.min(halfWidth, halfHeight) * 0.5F * scale;
        band = Math.max(0.5F, Math.min(band, Math.min(halfWidth, halfHeight) - 0.5F));

        RoundedRect outerPath = new RoundedRect(centerX, centerY, halfWidth, halfHeight, radius);
        RoundedRect innerPath = new RoundedRect(centerX, centerY,
                halfWidth - band, halfHeight - band, radius - band);

        int segments = outerPath.segmentCount();
        VertexConsumer buffer = graphics.bufferSource().getBuffer(RenderType.guiOverlay());
        Matrix4f matrix = graphics.pose().last().pose();

        float[] o1 = new float[2];
        float[] o2 = new float[2];
        float[] i1 = new float[2];
        float[] i2 = new float[2];

        for (int i = 0; i < segments; i++) {
            outerPath.point((float) i / segments, o1);
            outerPath.point((float) (i + 1) / segments, o2);
            innerPath.point((float) i / segments, i1);
            innerPath.point((float) (i + 1) / segments, i2);

            // Winding matters: RenderType.guiOverlay() carries no cull-state shard,
            // so the GL cull state left behind by the previous GUI draw still
            // applies. RimLightEffect's quads wind inner-first; matching that keeps
            // the ring from being back-face culled.
            vertex(buffer, matrix, i1[0], i1[1], bilinear(inner, i1, left, top, fullWidth, fullHeight));
            vertex(buffer, matrix, i2[0], i2[1], bilinear(inner, i2, left, top, fullWidth, fullHeight));
            vertex(buffer, matrix, o2[0], o2[1], bilinear(outer, o2, left, top, fullWidth, fullHeight));
            vertex(buffer, matrix, o1[0], o1[1], bilinear(outer, o1, left, top, fullWidth, fullHeight));
        }
        return true;
    }

    /**
     * {@code ShimmerEffect.renderBack}: a band between the 3 px outset rectangle and a
     * control rectangle inset by 12 px. The inner edge is coloured with
     * {@code innerColor.lerp(accentColor, t)} where
     * {@code t = 0.5 + 0.5 * cos(angle * frequency + time * speed)} and
     * {@code angle = -atan2(point.y - centre.y, point.x - centre.x)} (see
     * {@link #wave}); the outer edge is a flat {@code outerColor}. Only the outline
     * changes here, so the travelling wave is preserved exactly.
     *
     * <p>ShimmerEffect also owns the additive "glowing renderer" GL state, so this
     * reproduces {@code TooltipHelper.enableGlowingRenderer()} /
     * {@code disableGlowingRenderer()} around the draw.
     */
    private static boolean renderShimmer(ShimmerEffect shimmer, TooltipState state, GuiGraphics graphics,
                                         int x, int y, int width, int height) {
        float left = x - EFFECT_OUTSET;
        float top = y - EFFECT_OUTSET;
        float fullWidth = width + 2.0F * EFFECT_OUTSET;
        float fullHeight = height + 2.0F * EFFECT_OUTSET;
        float controlWidth = fullWidth - 2.0F * SHIMMER_CONTROL_INSET;
        float controlHeight = fullHeight - 2.0F * SHIMMER_CONTROL_INSET;
        if (controlWidth <= 0.0F || controlHeight <= 0.0F) {
            return false;
        }

        float halfWidth = fullWidth * 0.5F;
        float halfHeight = fullHeight * 0.5F;
        float centerX = left + halfWidth;
        float centerY = top + halfHeight;
        float radius = effectRadius(halfWidth, halfHeight);

        RoundedRect innerPath = new RoundedRect(centerX, centerY, halfWidth, halfHeight, radius);
        RoundedRect controlPath = new RoundedRect(centerX, centerY,
                controlWidth * 0.5F, controlHeight * 0.5F, radius - SHIMMER_CONTROL_INSET);

        ARGB innerColor = shimmer.innerColor().get();
        ARGB accentColor = shimmer.accentColor().get();
        ARGB outerColor = shimmer.outerColor().get();
        float frequency = shimmer.frequency();
        float phase = state.timeInSeconds() * shimmer.speed();

        int segments = innerPath.segmentCount();
        VertexConsumer buffer = graphics.bufferSource().getBuffer(RenderType.guiOverlay());
        Matrix4f matrix = graphics.pose().last().pose();

        float[] in1 = new float[2];
        float[] in2 = new float[2];
        float[] c1 = new float[2];
        float[] c2 = new float[2];

        enableGlowingRenderer();
        try {
            for (int i = 0; i < segments; i++) {
                float u1 = (float) i / segments;
                float u2 = (float) (i + 1) / segments;
                innerPath.point(u1, in1);
                innerPath.point(u2, in2);
                controlPath.point(u1, c1);
                controlPath.point(u2, c2);

                float t1 = wave(in1, centerX, centerY, frequency, phase);
                float t2 = wave(in2, centerX, centerY, frequency, phase);
                float f1 = 0.3F + 0.2F * t1;
                float f2 = 0.3F + 0.2F * t2;

                vertex(buffer, matrix, in2[0], in2[1], innerColor.lerp(accentColor, t2));
                vertex(buffer, matrix, in1[0], in1[1], innerColor.lerp(accentColor, t1));
                vertex(buffer, matrix, in1[0] + (c1[0] - in1[0]) * f1, in1[1] + (c1[1] - in1[1]) * f1, outerColor);
                vertex(buffer, matrix, in2[0] + (c2[0] - in2[0]) * f2, in2[1] + (c2[1] - in2[1]) * f2, outerColor);
            }
        } finally {
            disableGlowingRenderer();
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** Mirrors {@code TooltipHelper.enableGlowingRenderer()}. */
    private static void enableGlowingRenderer() {
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);
    }

    /** Mirrors {@code TooltipHelper.disableGlowingRenderer()}. */
    private static void disableGlowingRenderer() {
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    /**
     * Corner radius of obscure's effect rectangles. They are ModernUI's panel
     * rectangle (layout box + {@value #PANEL_INSET} px, radius {@code sCornerRadius})
     * inset by the difference, so the radius shrinks by the same amount. Returns 0
     * when the radius cannot be read, which degenerates to the original square
     * outline.
     */
    private static float effectRadius(float halfWidth, float halfHeight) {
        float panelRadius;
        try {
            panelRadius = cornerRadiusField.getFloat(null);
        } catch (Throwable ignored) {
            return 0.0F;
        }
        float radius = panelRadius - (PANEL_INSET - EFFECT_OUTSET);
        return Math.max(0.0F, Math.min(radius, Math.min(halfWidth, halfHeight)));
    }

    /**
     * {@code ShimmerEffect} computes its travelling wave from
     * {@code -atan2(point.y - centre.y, point.x - centre.x)}. Reproduced verbatim.
     */
    private static float wave(float[] point, float centerX, float centerY, float frequency, float phase) {
        float angle = (float) -Math.atan2(point[1] - centerY, point[0] - centerX);
        return 0.5F + 0.5F * (float) Math.cos(angle * frequency + phase);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix, float x, float y, ARGB color) {
        buffer.vertex(matrix, x, y, 0.0F)
                .color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha())
                .endVertex();
    }

    private static ARGB bilinear(QuadPalette palette, float[] point, float left, float top,
                                 float width, float height) {
        float u = clamp((point[0] - left) / width);
        float v = clamp((point[1] - top) / height);
        ARGB topMix = palette.topLeft().get().lerp(palette.topRight().get(), u);
        ARGB bottomMix = palette.bottomLeft().get().lerp(palette.bottomRight().get(), u);
        return topMix.lerp(bottomMix, v);
    }

    private static float clamp(float value) {
        return value < 0.0F ? 0.0F : (value > 1.0F ? 1.0F : value);
    }

    /**
     * A rounded-rectangle outline sampled by arc length, walked clockwise from the
     * end of the top-left corner (screen coordinates, y pointing down). This mirrors
     * the rectangle parameterisation of obscure-tooltips' effects: the same normalised
     * position on either path is the same point across the band, so the effects'
     * inward lerps keep working unchanged.
     */
    private static final class RoundedRect {

        private static final float HALF_PI = (float) (Math.PI * 0.5);
        private static final float PI = (float) Math.PI;

        private final float centerX;
        private final float centerY;
        private final float halfWidth;
        private final float halfHeight;
        private final float radius;
        private final float total;
        private final float[] lengths = new float[8];

        RoundedRect(float centerX, float centerY, float halfWidth, float halfHeight, float radius) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.halfWidth = Math.max(0.0F, halfWidth);
            this.halfHeight = Math.max(0.0F, halfHeight);
            this.radius = Math.max(0.0F, Math.min(radius, Math.min(this.halfWidth, this.halfHeight)));

            float straightWidth = 2.0F * (this.halfWidth - this.radius);
            float straightHeight = 2.0F * (this.halfHeight - this.radius);
            float corner = (float) (Math.PI * this.radius * 0.5);

            this.lengths[0] = straightWidth;  // top edge, left to right
            this.lengths[1] = corner;         // top-right corner
            this.lengths[2] = straightHeight; // right edge, top to bottom
            this.lengths[3] = corner;         // bottom-right corner
            this.lengths[4] = straightWidth;  // bottom edge, right to left
            this.lengths[5] = corner;         // bottom-left corner
            this.lengths[6] = straightHeight; // left edge, bottom to top
            this.lengths[7] = corner;         // top-left corner

            this.total = 2.0F * straightWidth + 2.0F * straightHeight + 4.0F * corner;
        }

        int segmentCount() {
            int count = (int) Math.ceil(this.total / SEGMENT_LENGTH);
            return Math.max(MIN_SEGMENTS, Math.min(MAX_SEGMENTS, count));
        }

        /** Writes {@code {x, y}} at normalised arc-length position {@code u} into {@code out}. */
        void point(float u, float[] out) {
            if (this.total <= 0.0F) {
                out[0] = this.centerX;
                out[1] = this.centerY;
                return;
            }

            float distance = u * this.total;
            int feature = 0;
            while (feature < 7 && distance > this.lengths[feature]) {
                distance -= this.lengths[feature];
                feature++;
            }
            float t = this.lengths[feature] > 0.0F ? distance / this.lengths[feature] : 0.0F;
            t = clamp(t);

            float left = this.centerX - this.halfWidth;
            float right = this.centerX + this.halfWidth;
            float top = this.centerY - this.halfHeight;
            float bottom = this.centerY + this.halfHeight;
            float r = this.radius;

            switch (feature) {
                case 0:
                    out[0] = left + r + (right - left - 2.0F * r) * t;
                    out[1] = top;
                    break;
                case 1:
                    arc(right - r, top + r, r, -HALF_PI, 0.0F, t, out);
                    break;
                case 2:
                    out[0] = right;
                    out[1] = top + r + (bottom - top - 2.0F * r) * t;
                    break;
                case 3:
                    arc(right - r, bottom - r, r, 0.0F, HALF_PI, t, out);
                    break;
                case 4:
                    out[0] = right - r - (right - left - 2.0F * r) * t;
                    out[1] = bottom;
                    break;
                case 5:
                    arc(left + r, bottom - r, r, HALF_PI, PI, t, out);
                    break;
                case 6:
                    out[0] = left;
                    out[1] = bottom - r - (bottom - top - 2.0F * r) * t;
                    break;
                default:
                    arc(left + r, top + r, r, PI, PI + HALF_PI, t, out);
                    break;
            }
        }

        private static void arc(float centerX, float centerY, float radius,
                                float from, float to, float t, float[] out) {
            float angle = from + (to - from) * t;
            out[0] = centerX + radius * (float) Math.cos(angle);
            out[1] = centerY + radius * (float) Math.sin(angle);
        }

        private static float clamp(float value) {
            return value < 0.0F ? 0.0F : (value > 1.0F ? 1.0F : value);
        }
    }
}
