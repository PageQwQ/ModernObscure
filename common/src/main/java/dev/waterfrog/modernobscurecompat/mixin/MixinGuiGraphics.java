package dev.waterfrog.modernobscurecompat.mixin;

import dev.waterfrog.modernobscurecompat.compat.ModernUITooltipGuard;
import dev.waterfrog.modernobscurecompat.compat.ObscureTooltips;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Optional;

/**
 * Fabric-side tooltip routing. Hides ModernUI's own tooltip option
 * ({@code TooltipRenderer.sTooltip}) while a tooltip that obscure-tooltips will
 * render is being prepared / drawn.
 *
 * <p>In 1.21.11 both ModernUI and obscure-tooltips inject into
 * {@code GuiGraphics.renderTooltip(Font, List, int, int, ClientTooltipPositioner, Identifier)}
 * at HEAD. Mixin runs lower-priority handlers first, so this mixin (priority 0)
 * runs before both; disabling the option here makes ModernUI's handler fall
 * through and leaves obscure-tooltips' own handler to draw the tooltip (which
 * this mod gives the ModernUI SDF background via
 * {@link MixinObscureTooltipRenderer}).
 *
 * <p>NeoForge patches an extra {@code ItemStack} parameter onto this method, so
 * the matching guard lives in {@code MixinGuiGraphicsNeoForge} in the NeoForge
 * module.
 */
@Mixin(value = GuiGraphics.class, priority = 0)
public abstract class MixinGuiGraphics {

    @Inject(method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;IILnet/minecraft/resources/Identifier;)V",
            at = @At("HEAD"))
    private void preSetTooltipForNextFrame(Font font, List<Component> lines,
                                           Optional<TooltipComponent> optional, int x, int y,
                                           Identifier identifier, CallbackInfo ci) {
        ModernUITooltipGuard.restore();
        if (optional.isPresent() && ObscureTooltips.hasStackBufferOptional(optional.get())) {
            ModernUITooltipGuard.saveAndDisable();
        }
    }

    @Inject(method = "setTooltipForNextFrame(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;IILnet/minecraft/resources/Identifier;)V",
            at = @At("TAIL"))
    private void postSetTooltipForNextFrame(Font font, List<Component> lines,
                                            Optional<TooltipComponent> optional, int x, int y,
                                            Identifier identifier, CallbackInfo ci) {
        ModernUITooltipGuard.restore();
    }

    @Inject(method = "renderTooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;Lnet/minecraft/resources/Identifier;)V",
            at = @At("HEAD"), cancellable = true)
    private void preRenderTooltip(Font font, List<ClientTooltipComponent> components,
                                  int x, int y, ClientTooltipPositioner positioner,
                                  Identifier identifier, CallbackInfo ci) {
        // Clear any leftover state from a previous render that was cancelled.
        ModernUITooltipGuard.restore();
        if (!ObscureTooltips.hasStackBuffer(components)) {
            return;
        }
        // Render obscure-tooltips ourselves at priority 0 so it always runs
        // before ModernUI's handler, then cancel so neither ModernUI nor
        // obscure-tooltips' own injector draws a second time.
        ModernUITooltipGuard.saveAndDisable();
        if (ObscureTooltips.render((GuiGraphics) (Object) this, font, components, x, y, positioner)) {
            ModernUITooltipGuard.restore();
            ci.cancel();
        }
    }

    @Inject(method = "renderTooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;Lnet/minecraft/resources/Identifier;)V",
            at = @At("TAIL"))
    private void postRenderTooltip(Font font, List<ClientTooltipComponent> components,
                                   int x, int y, ClientTooltipPositioner positioner,
                                   Identifier identifier, CallbackInfo ci) {
        ModernUITooltipGuard.restore();
    }
}
