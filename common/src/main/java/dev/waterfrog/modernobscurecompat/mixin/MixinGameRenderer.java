package dev.waterfrog.modernobscurecompat.mixin;

import dev.waterfrog.modernobscurecompat.compat.ModernUITooltipGuard;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Catch-all restore for the ModernUI tooltip option at the end of every frame.
 * Covers the rare path where obscure-tooltips' own renderTooltipInternal handler
 * cancels after our handler already fell through (its early return skips both
 * the vanilla body and our TAIL injector).
 */
@Mixin(GameRenderer.class)
public abstract class MixinGameRenderer {

    @Inject(method = "render", at = @At("TAIL"))
    private void afterRenderFrame(DeltaTracker deltaTracker, boolean renderLevel,
                                  CallbackInfo ci) {
        ModernUITooltipGuard.restore();
    }
}
