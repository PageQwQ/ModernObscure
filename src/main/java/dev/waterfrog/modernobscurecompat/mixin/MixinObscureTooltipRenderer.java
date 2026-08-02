package dev.waterfrog.modernobscurecompat.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.obscuria.tooltips.client.TooltipRenderer;
import dev.obscuria.tooltips.client.TooltipState;
import dev.waterfrog.modernobscurecompat.compat.CompatState;
import dev.waterfrog.modernobscurecompat.compat.ModernUIBackgroundRenderer;
import dev.waterfrog.modernobscurecompat.debug.RenderDebug;
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

    @Unique
    private static volatile Boolean modernUIAvailable;

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

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private static void guardDoubleRender(GuiGraphics graphics, Font font,
                                          List<ClientTooltipComponent> components,
                                          int mouseX, int mouseY,
                                          ClientTooltipPositioner positioner,
                                          CallbackInfoReturnable<Boolean> cir) {
        RenderDebug.startFrame("obscure.TooltipRenderer.render");
        RenderDebug.step("HEAD", "guardDoubleRender - entry");
        if (CompatState.isRendering()) {
            RenderDebug.step("HEAD", "double render blocked -> return false");
            cir.setReturnValue(false);
            return;
        }
        CompatState.setRendering(true);
        RenderDebug.step("HEAD", "setRendering=true, proceed");
    }

    @Inject(method = "render", at = @At("RETURN"))
    private static void afterRender(GuiGraphics graphics, Font font,
                                    List<ClientTooltipComponent> components,
                                    int mouseX, int mouseY,
                                    ClientTooltipPositioner positioner,
                                    CallbackInfoReturnable<Boolean> cir) {
        RenderDebug.step("RETURN", "afterRender - final flush");
        graphics.flush();
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Ldev/obscuria/tooltips/client/TooltipState;renderPanel(Lnet/minecraft/client/gui/GuiGraphics;Lorg/joml/Vector2ic;II)V"))
    private static void redirectRenderPanel(TooltipState state, GuiGraphics graphics, Vector2ic pos, int width, int height) {
        RenderDebug.step("PANEL", "redirect — pos=(" + pos.x() + "," + pos.y() + ") size=" + width + "x" + height);
        if (!isModernUIAvailable()) {
            RenderDebug.step("PANEL", "ModernUI not available, original renderPanel");
            state.renderPanel(graphics, pos, width, height);
            return;
        }
        try {
            RenderDebug.step("PANEL", "buffer SDF bg via ModernUI");
            Matrix4f pose = graphics.pose().last().pose();
            ModernUIBackgroundRenderer.drawRoundedBackground(
                    graphics, pose, (float) pos.x(), (float) pos.y(), width, height, state);
            RenderDebug.step("PANEL", "SDF bg buffered");

            // Flush SDF background and reset render state.
            // ModernUI's SDF shader writes depth values at the tooltip z-level.
            // AppleSkin's onRenderTooltip calls enableDepthTest(), and the default
            // depth function GL_LESS would reject food bars at the same z as the bg.
            // Set depthFunc to GL_ALWAYS (519) so depth test always passes.
            graphics.flush();
            RenderSystem.disableDepthTest();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthFunc(519); // GL_ALWAYS — depth test always passes
            RenderDebug.step("PANEL", "render state reset after SDF, depthFunc=GL_ALWAYS");
        } catch (Exception e) {
            RenderDebug.step("PANEL", "SDF bg failed, fallback: " + e.getClass().getSimpleName());
            state.renderPanel(graphics, pos, width, height);
        }
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Ldev/obscuria/tooltips/client/TooltipState;renderFrame(Lnet/minecraft/client/gui/GuiGraphics;Lorg/joml/Vector2ic;II)V"))
    private static void redirectRenderFrame(TooltipState state, GuiGraphics graphics, Vector2ic pos, int width, int height) {
        RenderDebug.step("FRAME", "redirect — pos=(" + pos.x() + "," + pos.y() + ") size=" + width + "x" + height);
        if (!isModernUIAvailable()) {
            RenderDebug.step("FRAME", "ModernUI not available, original renderFrame");
            state.renderFrame(graphics, pos, width, height);
            return;
        }
        RenderDebug.step("FRAME", "SKIP — SDF background already buffered");
    }
}