package dev.waterfrog.modernobscurecompat.mixin;

import dev.waterfrog.modernobscurecompat.compat.ModernUITooltipGuard;
import dev.waterfrog.modernobscurecompat.compat.ObscureTooltips;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * NeoForge-side tooltip routing. NeoForge patches an extra {@code ItemStack}
 * parameter onto {@code GuiGraphics.renderTooltip}, and both ModernUI and
 * obscure-tooltips hook that 7-argument overload at HEAD on this loader. This
 * mixin runs first (priority 0) and hides ModernUI's tooltip option so its
 * handler falls through and obscure-tooltips draws the tooltip.
 */
@Mixin(value = GuiGraphics.class, priority = 0)
public abstract class MixinGuiGraphicsNeoForge {

    @Inject(method = "renderTooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;Lnet/minecraft/resources/Identifier;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"), cancellable = true)
    private void preRenderTooltip(Font font, List<ClientTooltipComponent> components,
                                  int x, int y, ClientTooltipPositioner positioner,
                                  Identifier identifier, ItemStack stack, CallbackInfo ci) {
        ModernUITooltipGuard.restore();
        if (!ObscureTooltips.hasStackBuffer(components)) {
            return;
        }
        ModernUITooltipGuard.saveAndDisable();
        if (ObscureTooltips.render((GuiGraphics) (Object) this, font, components, x, y, positioner)) {
            ModernUITooltipGuard.restore();
            ci.cancel();
        }
    }

    @Inject(method = "renderTooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;IILnet/minecraft/client/gui/screens/inventory/tooltip/ClientTooltipPositioner;Lnet/minecraft/resources/Identifier;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("TAIL"))
    private void postRenderTooltip(Font font, List<ClientTooltipComponent> components,
                                   int x, int y, ClientTooltipPositioner positioner,
                                   Identifier identifier, ItemStack stack, CallbackInfo ci) {
        ModernUITooltipGuard.restore();
    }
}
