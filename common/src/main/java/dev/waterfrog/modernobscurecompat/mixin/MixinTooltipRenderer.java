package dev.waterfrog.modernobscurecompat.mixin;

import dev.obscuria.tooltips.client.TooltipRenderer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TooltipRenderer.class)
public abstract class MixinTooltipRenderer {

    // In 1.21.11 armor is data-driven through the EQUIPPABLE component. Body-slot
    // equipment (wolf/horse armor) is not rendered on armor stands, which would
    // leave an empty stand in obscure-tooltips' preview; skip those items.
    @Inject(method = "shouldShowArmorPreview", at = @At("HEAD"), cancellable = true)
    private static void skipBodySlotArmorPreview(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        Equippable equippable = stack.get(DataComponents.EQUIPPABLE);
        if (equippable != null && equippable.slot() == EquipmentSlot.BODY) {
            cir.setReturnValue(false);
        }
    }
}
