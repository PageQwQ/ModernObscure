package dev.waterfrog.modernobscurecompat.mixin;

import dev.obscuria.tooltips.client.TooltipRenderer;
import dev.obscuria.tooltips.client.TooltipState;
import dev.waterfrog.modernobscurecompat.compat.ModernUIBackgroundRenderer;
import dev.waterfrog.modernobscurecompat.compat.ModernUIRoundedEffects;
import dev.waterfrog.modernobscurecompat.compat.ModernUITooltipGuard;
import dev.waterfrog.modernobscurecompat.compat.TooltipTransition;
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
        // Only round the panel glow when the rounded panel really is on screen.
        ModernUIRoundedEffects.setRoundedPanelActive(rendered);
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

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;positionTooltip(IIIIII)Lorg/joml/Vector2ic;"))
    private static Vector2ic glidePositionTooltip(ClientTooltipPositioner positioner,
                                                  int screenWidth, int screenHeight,
                                                  int mouseX, int mouseY,
                                                  int tooltipWidth, int tooltipHeight) {
        Vector2ic target = positioner.positionTooltip(
                screenWidth, screenHeight, mouseX, mouseY, tooltipWidth, tooltipHeight);
        return TooltipTransition.animate(target.x(), target.y(), tooltipWidth, tooltipHeight);
    }

    /**
     * Applies the size transition (pop-in / resize) to the whole tooltip.
     *
     * <p>Injected right after obscure-tooltips pushes its outer frame matrix, so
     * the scale lands in the base matrix and covers the ModernUI SDF background,
     * the frame, effects and every content component alike. In 1.21.11
     * {@code GuiGraphics.pose()} is a JOML {@code Matrix3x2fStack}, and the
     * content is drawn through {@code graphics.pose()} as well, so everything
     * scales together. obscure-tooltips pops that same matrix at the end of
     * {@code render()}, so the transform is removed automatically.
     */
    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lorg/joml/Matrix3x2fStack;pushMatrix()Lorg/joml/Matrix3x2fStack;",
            ordinal = 0, shift = At.Shift.AFTER, remap = false), require = 0)
    private static void scaleContentOnFramePose(GuiGraphics graphics, Font font,
                                                List<ClientTooltipComponent> components,
                                                int mouseX, int mouseY,
                                                ClientTooltipPositioner positioner,
                                                CallbackInfoReturnable<Boolean> cir) {
        applySizeTransition(graphics, mouseX, mouseY);
    }

    @Unique
    private static void applySizeTransition(GuiGraphics graphics, int mouseX, int mouseY) {
        float sx = TooltipTransition.getScaleX();
        float sy = TooltipTransition.getScaleY();
        if (sx == 1.0f && sy == 1.0f) {
            return;
        }
        graphics.pose().translate(mouseX, mouseY);
        graphics.pose().scale(sx, sy);
        graphics.pose().translate(-mouseX, -mouseY);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private static void afterRender(GuiGraphics graphics, Font font,
                                    List<ClientTooltipComponent> components,
                                    int mouseX, int mouseY, ClientTooltipPositioner positioner,
                                    CallbackInfoReturnable<Boolean> cir) {
        ModernUIRoundedEffects.setRoundedPanelActive(false);
        // Let ModernUI bind its tooltip uniforms for this frame again.
        ModernUITooltipGuard.restore();
    }
}
