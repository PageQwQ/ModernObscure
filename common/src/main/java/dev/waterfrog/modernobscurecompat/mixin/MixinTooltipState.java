package dev.waterfrog.modernobscurecompat.mixin;

import dev.obscuria.tooltips.client.TooltipState;
import dev.obscuria.tooltips.client.tooltip.element.effect.TooltipEffect;
import dev.waterfrog.modernobscurecompat.compat.CompatState;
import dev.waterfrog.modernobscurecompat.compat.ModernUIRoundedEffects;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Vector2ic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets the compat replace obscure-tooltips' rectangular "panel glow" effects
 * (rim light, shimmer) with rounded ones when ModernUI draws the rounded tooltip
 * background.
 *
 * <p>obscure-tooltips 3.10.1 submits each back effect through
 * {@code graphics.drawManaged(() -> effect.renderBack(...))}, so the
 * {@code renderBack} call sits inside a synthetic lambda and cannot be reached by
 * a {@code @Redirect} on {@code renderEffects}. The whole loop is therefore
 * replayed here instead — identical apart from the same {@code drawManaged}
 * wrapper — and cancelled so the original body does not run twice.
 *
 * <p>{@link ModernUIRoundedEffects} returns {@code false} for every effect it does
 * not handle (and whenever ModernUI/obscure internals do not match), in which case
 * the effect is submitted exactly as obscure-tooltips would have done.
 */
@Mixin(TooltipState.class)
public abstract class MixinTooltipState {

    @Inject(method = "renderEffects", at = @At("HEAD"), cancellable = true)
    private void modernobscure$roundedPanelEffects(GuiGraphics graphics, Vector2ic pos,
                                                   int width, int height, CallbackInfo ci) {
        // Only when ModernUI's rounded panel really replaced obscure-tooltips' own.
        if (!CompatState.isInContentWindow()) {
            return;
        }

        TooltipState state = (TooltipState) (Object) this;
        for (TooltipEffect effect : state.style.effects()) {
            if (ModernUIRoundedEffects.tryRender(effect, state, graphics, pos.x(), pos.y(), width, height)) {
                continue;
            }
            graphics.drawManaged(() -> effect.renderBack(state, graphics, pos.x(), pos.y(), width, height));
        }
        ci.cancel();
    }
}
