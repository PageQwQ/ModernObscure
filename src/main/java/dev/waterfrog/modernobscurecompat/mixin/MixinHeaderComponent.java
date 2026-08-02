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

    @Inject(method = "method_32666", at = @At("HEAD"), remap = false)
    private void beforeRenderImage(Font font, int x, int y, GuiGraphics graphics, CallbackInfo ci) {
        if (!isModernUIAvailable()) return;
        // Reset shader color to white before drawing the slot texture.
        // obscure-tooltips' icon rendering (e.g. AccentIcon.renderItem) may
        // leave the shader color tinted from the item's color multiplier,
        // which would cause our item_slot texture to render with a wrong tint.
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderDebug.step("SLOT", "drawing item_slot at (" + x + "," + y + ")");
        graphics.blit(ITEM_SLOT, x + 1, y + 1, 0, 0, 18, 18, 18, 18);
    }
}