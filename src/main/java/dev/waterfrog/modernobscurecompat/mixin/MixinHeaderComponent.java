package dev.waterfrog.modernobscurecompat.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
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

    @Inject(method = "method_32666", at = @At("RETURN"), remap = false)
    private void afterRenderImage(Font font, int x, int y, GuiGraphics graphics, CallbackInfo ci) {
        if (!isModernUIAvailable()) return;
        // Draw item_slot AFTER the original renderImage method completes.
        // The original method renders:
        //   1. ColorRectSlot (semi-transparent white fill)
        //   2. Effects (icon effects)
        //   3. Icon (item icon, may set shader color)
        // By rendering at RETURN, our slot is on top of the ColorRectSlot
        // (preventing its white fill from washing out our texture), and
        // we reset shader color to white to undo any tint from icon rendering.
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderDebug.step("SLOT", "drawing item_slot at (" + x + "," + y + ")");
        graphics.blit(ITEM_SLOT, x + 1, y + 1, 0, 0, 18, 18, 18, 18);
    }
}