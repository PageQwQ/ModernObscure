package dev.waterfrog.modernobscurecompat.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.waterfrog.modernobscurecompat.compat.CompatState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While a compat tooltip is on screen, freeze the RenderSystem fog state.
 *
 * ModernUI's glyph shaders (rendertype_modern_text_*) and vanilla's
 * rendertype_text apply linear fog from RenderSystem statics at batch-draw
 * time. Custom tooltip components from ANY mod (TACZ ammo icons, AppleSkin
 * overlays, Peek/Shulker Box Tooltip previews...) can flush the shared
 * buffer mid-loop via GuiGraphics.renderItem/blit; if any of that code (or
 * an event hook it fires) touches fog, every glyph buffered so far is drawn
 * fogged to FogColor (black) — invisible text. GUI rendering never uses fog,
 * so during the tooltip window we simply drop all fog mutations; the pushed-
 * out values established by MixinObscureTooltipRenderer stay in effect for
 * every draw until the window closes.
 *
 * RenderSystem carries Mojang's @DontObfuscate: these method names are
 * identical on Fabric (mojmap-in-production classes) and Forge runtime, so a
 * single remap=false target covers both loaders.
 */
@Mixin(value = RenderSystem.class, remap = false)
public abstract class MixinRenderSystem {

    @Inject(method = "setShaderFogStart", at = @At("HEAD"), cancellable = true)
    private static void guardFogStart(float start, CallbackInfo ci) {
        if (CompatState.isInContentWindow()) {
            ci.cancel();
        }
    }

    @Inject(method = "setShaderFogEnd", at = @At("HEAD"), cancellable = true)
    private static void guardFogEnd(float end, CallbackInfo ci) {
        if (CompatState.isInContentWindow()) {
            ci.cancel();
        }
    }
}
