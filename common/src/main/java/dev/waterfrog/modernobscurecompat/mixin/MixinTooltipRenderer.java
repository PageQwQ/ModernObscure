package dev.waterfrog.modernobscurecompat.mixin;

import dev.obscuria.tooltips.client.TooltipRenderer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TooltipRenderer.class)
public abstract class MixinTooltipRenderer {

    // In 1.21.1 wolf/horse armor are AnimalArmorItem (extends ArmorItem) with the BODY
    // equipment slot, which armor stands don't render — obscure-tooltips would then show
    // an empty armor stand. Skip the preview for any body-slot armor item.
    @Inject(method = "shouldShowArmorPreview", at = @At("HEAD"), cancellable = true)
    private static void skipBodySlotArmorPreview(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (stack.getItem() instanceof ArmorItem armorItem
                && armorItem.getEquipmentSlot() == EquipmentSlot.BODY) {
            cir.setReturnValue(false);
        }
    }
}
