package dev.waterfrog.modernobscurecompat.mixin;

import dev.obscuria.tooltips.client.TooltipState;
import dev.obscuria.tooltips.client.tooltip.element.effect.TooltipEffect;
import dev.waterfrog.modernobscurecompat.compat.ModernUIRoundedEffects;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lets the compat replace obscure-tooltips' rectangular "panel glow" effects
 * (rim light, shimmer) with rounded ones when ModernUI draws the rounded tooltip
 * background.
 *
 * <p>{@link ModernUIRoundedEffects} returns {@code false} for every effect it does
 * not handle (and whenever ModernUI/obscure internals do not match), in which
 * case the original effect draws unchanged. The "rounded panel active" gate ties
 * the rounding to the tooltip whose SDF background actually replaced the panel.
 */
@Mixin(TooltipState.class)
public abstract class MixinTooltipState {

    @Redirect(
            method = "renderEffects",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/obscuria/tooltips/client/tooltip/element/effect/TooltipEffect;renderBack(Ldev/obscuria/tooltips/client/TooltipState;Lnet/minecraft/client/gui/GuiGraphics;IIII)V"))
    private void modernobscure$roundedPanelEffects(TooltipEffect effect, TooltipState state, GuiGraphics graphics,
                                                   int x, int y, int width, int height) {
        if (ModernUIRoundedEffects.isRoundedPanelActive()
                && ModernUIRoundedEffects.tryRender(effect, state, graphics, x, y, width, height)) {
            return;
        }
        effect.renderBack(state, graphics, x, y, width, height);
    }
}
