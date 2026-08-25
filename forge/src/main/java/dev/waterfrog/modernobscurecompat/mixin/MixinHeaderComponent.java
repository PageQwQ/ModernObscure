package dev.waterfrog.modernobscurecompat.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.obscuria.tooltips.client.component.HeaderComponent;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
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

    // Forge 1.20.1 jar of obscure-tooltips is SRG-obfuscated at runtime,
    // so the method is m_183452_ (javap-confirmed), not renderImage.
    @Inject(method = "m_183452_", at = @At("HEAD"), remap = false)
    private void beforeRenderImage(Font font, int x, int y, GuiGraphics graphics, CallbackInfo ci) {
        if (!isModernUIAvailable()) return;
        // obscure's own style.slot() renders the slot background; only reset
        // state that may have been changed by style effects (RayGlow disables
        // blend, RGB-disabling shader colors) before the icon renders.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Inject(method = "m_183452_", at = @At("RETURN"), remap = false)
    private void afterRenderImage(Font font, int x, int y, GuiGraphics graphics, CallbackInfo ci) {
        if (!isModernUIAvailable()) return;
        // Reset shader color to white after icon rendering.
        // AccentIcon.renderItem may set shader color from item tint.
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}