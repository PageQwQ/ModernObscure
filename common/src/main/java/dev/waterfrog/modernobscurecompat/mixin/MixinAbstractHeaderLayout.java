package dev.waterfrog.modernobscurecompat.mixin;

import dev.obscuria.fragmentum.content.util.color.ARGB;
import dev.obscuria.tooltips.client.TooltipState;
import dev.obscuria.tooltips.client.tooltip.element.panel.TooltipPanel;
import dev.obscuria.tooltips.client.tooltip.layout.AbstractHeaderLayout;
import dev.waterfrog.modernobscurecompat.compat.ModernUITooltipGuard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * obscure-tooltips derives the header separator color from the panel's border
 * palette (the default palette is violet). When ModernUI's rounded SDF
 * background is used, match obscure-tooltips' own soft white separator
 * (the same color styles without a panel already use) instead.
 */
@Mixin(AbstractHeaderLayout.class)
public abstract class MixinAbstractHeaderLayout {

    @Inject(method = "pickSeparatorColor", at = @At("RETURN"), cancellable = true)
    private void modernobscure$whiteSeparator(TooltipState state, CallbackInfoReturnable<ARGB> cir) {
        if (ModernUITooltipGuard.isAvailable()) {
            cir.setReturnValue(TooltipPanel.DEFAULT_SEPARATOR_COLOR);
        }
    }
}
