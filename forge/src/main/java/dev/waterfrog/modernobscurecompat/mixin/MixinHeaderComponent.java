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
    private static final ResourceLocation ITEM_SLOT = new ResourceLocation("modernobscurecompat", "textures/gui/item_slot.png");

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
        // item_slot.png 的半透明边框必须带混合渲染；epic 等带特效的样式
        // （RayGlow 会调用 disableBlend）会关闭 blend，导致边框不透明变白。
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // Reset shader color to white before drawing the slot texture.
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.blit(ITEM_SLOT, x + 1, y + 1, 0, 0, 18, 18, 18, 18);
    }

    @Inject(method = "m_183452_", at = @At("RETURN"), remap = false)
    private void afterRenderImage(Font font, int x, int y, GuiGraphics graphics, CallbackInfo ci) {
        if (!isModernUIAvailable()) return;
        // Reset shader color to white after icon rendering.
        // AccentIcon.renderItem may set shader color from item tint.
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}