package dev.waterfrog.modernobscurecompat.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.obscuria.tooltips.client.component.HeaderComponent;
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

    // Fabric jar of obscure-tooltips names this method_32666 (intermediary),
    // NeoForge jar names it renderImage (Mojang). Regex matches either.
    @Inject(method = "method_32666", at = @At("HEAD"), remap = false)
    private void beforeRenderImage(Font font, int x, int y, GuiGraphics graphics, CallbackInfo ci) {
        if (!isModernUIAvailable()) return;
        // obscure's own style.slot() renders the slot background; only reset
        // state that may have been changed by style effects (RayGlow disables
        // blend) before the icon renders.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Inject(method = "method_32666", at = @At("RETURN"), remap = false)
    private void afterRenderImage(Font font, int x, int y, GuiGraphics graphics, CallbackInfo ci) {
        if (!isModernUIAvailable()) return;
        // Reset shader color to white after icon rendering.
        // AccentIcon.renderItem may set shader color from item tint.
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}