package dev.waterfrog.modernobscurecompat.mixin;

import dev.obscuria.fragmentum.util.color.ARGB;
import dev.obscuria.tooltips.client.TooltipState;
import dev.obscuria.tooltips.client.tooltip.layout.AbstractHeaderLayout;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 1.20.1 (obscure-tooltips 3.10): the header separator color lerps from the
 * panel border palette, whose default is obscure's violet (#505000FF).
 * Under ModernUI the SDF background replaces the panel art, and a violet
 * line looks out of place — return a subtle white instead, matching the
 * #40FFFFFF separator this compat shipped originally.
 *
 * Only applies while ModernUI is present; without it obscure keeps its
 * native violet look.
 */
@Mixin(value = AbstractHeaderLayout.class, remap = false)
public abstract class MixinAbstractHeaderLayout {

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

    @Inject(method = "pickSeparatorColor", at = @At("HEAD"), cancellable = true)
    private void modernSeparatorColor(TooltipState state, CallbackInfoReturnable<ARGB> cir) {
        if (isModernUIAvailable()) {
            cir.setReturnValue(new ARGB(0.25F, 1.0F, 1.0F, 1.0F));
        }
    }
}
