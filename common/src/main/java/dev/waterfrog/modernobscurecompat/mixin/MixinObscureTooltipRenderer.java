package dev.waterfrog.modernobscurecompat.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.obscuria.tooltips.client.TooltipRenderer;
import dev.obscuria.tooltips.client.TooltipState;
import dev.waterfrog.modernobscurecompat.compat.CompatState;
import dev.waterfrog.modernobscurecompat.compat.ModernUIBackgroundRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import org.joml.Matrix4f;
import org.joml.Vector2ic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(value = TooltipRenderer.class, remap = true)
public abstract class MixinObscureTooltipRenderer {

    /**
     * Lift applied to ALL tooltip content (text glyphs and images alike)
     * while the ModernUI SDF background is active. The SDF background is
     * drawn with GuiRenderType.tooltip(), whose state shards enable
     * LEQUAL depth test WITH depth writes at z≈400 across the whole panel;
     * content render types re-apply LEQUAL at draw time no matter what GL
     * state we set beforehand. Content vertices are CPU-transformed through
     * a different matrix path than the background quad, so equal-z fragments
     * can lose the LEQUAL test by floating-point rounding on some GPUs —
     * ModernUI's own TooltipRenderer works around this exact issue by
     * lifting its text by 0.1 ("text to be discarded by LEqual depth test
     * on some GPUs"). obscure-tooltips' component loop has no such lift,
     * so we add one here for every component it renders.
     */
    @Unique
    private static final float CONTENT_Z_LIFT = 0.1F;

    @Unique
    private static volatile Boolean modernUIAvailable;

    @Unique
    private static final ThreadLocal<List<ClientTooltipComponent>> savedComponents = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<Font> savedFont = new ThreadLocal<>();

    @Unique
    private static boolean isModernUIAvailable() {
        if (modernUIAvailable == null) {
            try {
                Class.forName("icyllis.modernui.mc.TooltipRenderer");
                modernUIAvailable = true;
            } catch (ClassNotFoundException e) {
                modernUIAvailable = false;
            }
        }
        return modernUIAvailable;
    }

    /**
     * Bakes the content z-lift into the base pose right before obscure pushes
     * its frame pose (the second pushPose in render()). The frame translate
     * and pop that follow are unaffected, and every matrix the component loop
     * captures afterwards (renderText AND renderImage) includes the lift, so
     * text, item icons and any custom-drawn content from other mods all clear
     * the SDF background's depth writes. PoseStack.pushPose has different
     * runtime names per loader/environment; exactly one of these variants can
     * match in a given environment (require = 0 keeps the others silent).
     */
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", ordinal = 1, remap = false), require = 0)
    private static void liftContentMojmap(GuiGraphics graphics, Font font,
                                          List<ClientTooltipComponent> components,
                                          int mouseX, int mouseY,
                                          ClientTooltipPositioner positioner,
                                          CallbackInfoReturnable<Boolean> cir) {
        if (isModernUIAvailable()) {
            graphics.pose().translate(0.0F, 0.0F, CONTENT_Z_LIFT);
        }
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;method_22903()V", ordinal = 1, remap = false), require = 0)
    private static void liftContentIntermediary(GuiGraphics graphics, Font font,
                                                List<ClientTooltipComponent> components,
                                                int mouseX, int mouseY,
                                                ClientTooltipPositioner positioner,
                                                CallbackInfoReturnable<Boolean> cir) {
        if (isModernUIAvailable()) {
            graphics.pose().translate(0.0F, 0.0F, CONTENT_Z_LIFT);
        }
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private static void guardDoubleRender(GuiGraphics graphics, Font font,
                                          List<ClientTooltipComponent> components,
                                          int mouseX, int mouseY,
                                          ClientTooltipPositioner positioner,
                                          CallbackInfoReturnable<Boolean> cir) {
        if (CompatState.isRendering()) {
            cir.setReturnValue(false);
            return;
        }
        CompatState.setRendering(true);
        savedComponents.set(components);
        savedFont.set(font);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private static void afterRender(GuiGraphics graphics, Font font,
                                    List<ClientTooltipComponent> components,
                                    int mouseX, int mouseY,
                                    ClientTooltipPositioner positioner,
                                    CallbackInfoReturnable<Boolean> cir) {
        // Buffered slot/icon geometry is only drawn at this flush. Epic styles disable
        // blend (RayGlow), so re-enable it here or the semi-transparent item_slot
        // texture would be flushed opaque white.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        // Re-assert pushed-out fog before the final flush in case anything in the
        // component loop changed it (the fog guard freezes setters; this covers
        // draws reading stale uniform values).
        RenderSystem.setShaderFogStart(10000.0F);
        RenderSystem.setShaderFogEnd(20000.0F);
        graphics.flush();

        savedComponents.remove();
        savedFont.remove();

        // Restore the vanilla default depth state. The SDF background leaves
        // depth written across the panel, and subsequent HUD/screen geometry
        // draws with the current GL state — GL_LESS strict comparison fails at
        // equal depth (GUI sprites sit at far-plane depth) and whole banners
        // vanish.
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(515); // GL_LEQUAL — vanilla default
        // All tooltip content (text + images from every mod) has been flushed;
        // release the fog guard so world/HUD rendering can use fog again.
        CompatState.closeContentWindow();
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Ldev/obscuria/tooltips/client/TooltipState;renderPanel(Lnet/minecraft/client/gui/GuiGraphics;Lorg/joml/Vector2ic;II)V"))
    private static void redirectRenderPanel(TooltipState state, GuiGraphics graphics, Vector2ic pos, int width, int height) {
        if (!isModernUIAvailable()) {
            state.renderPanel(graphics, pos, width, height);
            return;
        }
        try {
            // Flush pending screen geometry BEFORE ModernUI's drawRoundedBackground
            // rewrites the model-view matrix. Without this, the whole screen's buffered
            // content is re-drawn with that matrix applied — shifted by the tooltip
            // center and lifted to z=400 — writing near depth across the entire screen
            // (Simulated's banners then fail the LEQUAL test and vanish entirely).
            graphics.flush();

            Matrix4f pose = graphics.pose().last().pose();

            ModernUIBackgroundRenderer.drawRoundedBackground(
                    graphics, pose, (float) pos.x(), (float) pos.y(), width, height, state);

            // Flush SDF background and reset render state.
            graphics.flush();
            RenderSystem.disableDepthTest();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthFunc(519); // GL_ALWAYS
            // Push fog out BEFORE any content is buffered (GUI text is drawn with
            // ModernUI's fogged glyph shaders), then freeze fog via MixinRenderSystem:
            // any mid-loop flush triggered by custom tooltip components draws
            // buffered glyphs with clean fog.
            RenderSystem.setShaderFogStart(10000.0F);
            RenderSystem.setShaderFogEnd(20000.0F);
            CompatState.openContentWindow();
        } catch (Exception e) {
            CompatState.closeContentWindow();
            state.renderPanel(graphics, pos, width, height);
        }
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Ldev/obscuria/tooltips/client/TooltipState;renderFrame(Lnet/minecraft/client/gui/GuiGraphics;Lorg/joml/Vector2ic;II)V"))
    private static void redirectRenderFrame(TooltipState state, GuiGraphics graphics, Vector2ic pos, int width, int height) {
        if (!isModernUIAvailable()) {
            state.renderFrame(graphics, pos, width, height);
            return;
        }
        // SKIP — SDF background already buffered with border included
    }
}
