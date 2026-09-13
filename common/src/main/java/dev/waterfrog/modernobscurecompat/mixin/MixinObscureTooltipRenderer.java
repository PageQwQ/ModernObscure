package dev.waterfrog.modernobscurecompat.mixin;

import dev.obscuria.tooltips.client.TooltipRenderer;
import dev.obscuria.tooltips.client.TooltipState;
import dev.waterfrog.modernobscurecompat.compat.ModernUIBackgroundRenderer;
import dev.waterfrog.modernobscurecompat.compat.ModernUITooltipGuard;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import org.joml.Vector2ic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Replaces obscure-tooltips' own tooltip panel/frame with ModernUI's rounded
 * SDF background when ModernUI is installed.
 *
 * <p>obscure-tooltips draws the panel through {@code TooltipState.renderPanel}
 * and the border through {@code TooltipState.renderFrame}. ModernUI's SDF
 * background already includes the border, so {@code renderFrame} is skipped.
 *
 * <p>ModernUI binds its {@code ModernTooltip} uniform buffer in
 * {@code GuiRenderer.executeDrawRange} only while {@code TooltipRenderer.sTooltip}
 * is true, so the option (hidden while ModernUI's own handler must skip) is
 * restored as soon as obscure-tooltips has finished submitting its draw.
 */
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

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Ldev/obscuria/tooltips/client/TooltipState;renderPanel(Lnet/minecraft/client/gui/GuiGraphics;Lorg/joml/Vector2ic;II)V"))
    private static void redirectRenderPanel(TooltipState state, GuiGraphics graphics, Vector2ic pos,
                                            int width, int height) {
        if (!isModernUIAvailable()) {
            state.renderPanel(graphics, pos, width, height);
            return;
        }
        boolean rendered = ModernUIBackgroundRenderer.drawRoundedBackground(
                graphics, (float) pos.x(), (float) pos.y(), width, height, state);
        if (!rendered) {
            state.renderPanel(graphics, pos, width, height);
        }
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Ldev/obscuria/tooltips/client/TooltipState;renderFrame(Lnet/minecraft/client/gui/GuiGraphics;Lorg/joml/Vector2ic;II)V"))
    private static void redirectRenderFrame(TooltipState state, GuiGraphics graphics, Vector2ic pos,
                                            int width, int height) {
        if (!isModernUIAvailable()) {
            state.renderFrame(graphics, pos, width, height);
        }
        // ModernUI: SKIP — the SDF background already draws the frame/border.
    }

    @Inject(method = "render", at = @At("RETURN"))
    private static void afterRender(GuiGraphics graphics, Font font,
                                    List<ClientTooltipComponent> components,
                                    int mouseX, int mouseY, ClientTooltipPositioner positioner,
                                    CallbackInfoReturnable<Boolean> cir) {
        // Let ModernUI bind its tooltip uniforms for this frame again.
        ModernUITooltipGuard.restore();
    }
}
