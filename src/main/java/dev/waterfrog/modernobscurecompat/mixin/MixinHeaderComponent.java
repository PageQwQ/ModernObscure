package dev.waterfrog.modernobscurecompat.mixin;

import dev.obscuria.tooltips.client.component.HeaderComponent;
import dev.waterfrog.modernobscurecompat.debug.RenderDebug;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = HeaderComponent.class, remap = false)
public abstract class MixinHeaderComponent {

    @Unique
    private static final ResourceLocation ITEM_SLOT = ResourceLocation.fromNamespaceAndPath("modernobscurecompat", "textures/gui/item_slot.png");

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

    @Inject(method = "method_32666", at = @At("HEAD"), remap = false)
    private void beforeRenderImage(Font font, int x, int y, GuiGraphics graphics, CallbackInfo ci) {
        if (!isModernUIAvailable()) return;
        // Draw item_slot texture behind the slot border and item icon.
        // obscure's ColorRectSlot draws with very low alpha (~12%) on the
        // SDF background, making it nearly invisible. Our item_slot.png
        // provides a clear visible slot border.
        // The slot renders at (x, y) with size 20x20; item icon is 16x16
        // centered. Draw slot texture 1px outside the icon for a visible border.
        RenderDebug.step("SLOT", "drawing item_slot at (" + x + "," + y + ")");
        graphics.blit(ITEM_SLOT, x + 1, y + 1, 0, 0, 18, 18, 18, 18);
    }
}