package dev.waterfrog.modernobscurecompat.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.obscuria.tooltips.client.TooltipRenderer;
import dev.obscuria.tooltips.client.TooltipState;
import dev.waterfrog.modernobscurecompat.compat.CompatState;
import dev.waterfrog.modernobscurecompat.compat.ModernUIBackgroundRenderer;
import dev.waterfrog.modernobscurecompat.debug.RenderDebug;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import org.joml.Matrix4f;
import org.joml.Vector2ic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.util.List;

@Mixin(value = TooltipRenderer.class, remap = true)
public abstract class MixinObscureTooltipRenderer {

    @Unique
    private static volatile Boolean modernUIAvailable;

    @Unique
    private static final ThreadLocal<List<ClientTooltipComponent>> savedComponents = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<Font> savedFont = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<Integer> savedContentX = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<Integer> savedContentY = new ThreadLocal<>();

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

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private static void guardDoubleRender(GuiGraphics graphics, Font font,
                                          List<ClientTooltipComponent> components,
                                          int mouseX, int mouseY,
                                          ClientTooltipPositioner positioner,
                                          CallbackInfoReturnable<Boolean> cir) {
        RenderDebug.startFrame("obscure.TooltipRenderer.render");
        RenderDebug.step("HEAD", "guardDoubleRender - entry");
        if (CompatState.isRendering()) {
            RenderDebug.step("HEAD", "double render blocked -> return false");
            cir.setReturnValue(false);
            return;
        }
        CompatState.setRendering(true);
        savedComponents.set(components);
        savedFont.set(font);
        RenderDebug.step("HEAD", "setRendering=true, proceed");
    }

    @Inject(method = "render", at = @At("RETURN"))
    private static void afterRender(GuiGraphics graphics, Font font,
                                    List<ClientTooltipComponent> components,
                                    int mouseX, int mouseY,
                                    ClientTooltipPositioner positioner,
                                    CallbackInfoReturnable<Boolean> cir) {
        RenderDebug.step("RETURN", "afterRender - final flush");
        graphics.flush();

        // Reset render state after SDF background.
        // ModernUI's SDF shader can leave depth/shader state corrupted,
        // which prevents AppleSkin food bars from rendering.
        RenderSystem.disableDepthTest();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        // Manually re-render AppleSkin FoodOverlay components.
        // The SDF background rendering may corrupt the shader/blend/depth state
        // that AppleSkin's drawItems() relies on, so we re-render here after
        // a clean state reset.
        List<ClientTooltipComponent> comps = savedComponents.get();
        Font fnt = savedFont.get();
        Integer cx = savedContentX.get();
        Integer cy = savedContentY.get();
        if (comps != null && fnt != null && cx != null && cy != null) {
            int y = cy;
            for (ClientTooltipComponent comp : comps) {
                if (comp.getClass().getName().equals("squeek.appleskin.client.TooltipOverlayHandler$FoodOverlay")) {
                    try {
                        // AppleSkin's FoodOverlay is compiled with Yarn mappings,
                        // so the method is named "drawItems" not "renderImage" (Mojmap).
                        // At the intermediary level the types are the same:
                        // drawItems(TextRenderer, int, int, DrawContext) →
                        // drawItems(class_327, int, int, class_332) which matches
                        // Font.class and GuiGraphics.class at runtime.
                        Method drawItems = comp.getClass().getMethod("drawItems", Font.class, int.class, int.class, GuiGraphics.class);
                        drawItems.invoke(comp, fnt, cx, y, graphics);
                        RenderDebug.step("AFTER", "re-rendered AppleSkin FoodOverlay at (" + cx + "," + y + ")");
                    } catch (Exception e) {
                        RenderDebug.step("AFTER", "AppleSkin re-render failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                    }
                }
                y += comp.getHeight();
            }
            graphics.flush();
        }
        savedComponents.remove();
        savedFont.remove();
        savedContentX.remove();
        savedContentY.remove();
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Ldev/obscuria/tooltips/client/TooltipState;renderPanel(Lnet/minecraft/client/gui/GuiGraphics;Lorg/joml/Vector2ic;II)V"))
    private static void redirectRenderPanel(TooltipState state, GuiGraphics graphics, Vector2ic pos, int width, int height) {
        RenderDebug.step("PANEL", "redirect — pos=(" + pos.x() + "," + pos.y() + ") size=" + width + "x" + height);
        if (!isModernUIAvailable()) {
            RenderDebug.step("PANEL", "ModernUI not available, original renderPanel");
            state.renderPanel(graphics, pos, width, height);
            return;
        }
        try {
            RenderDebug.step("PANEL", "buffer SDF bg via ModernUI");
            Matrix4f pose = graphics.pose().last().pose();
            // Save content position for AppleSkin rendering in afterRender.
            // obscure default margin is 3, content renders at (pos + margin).
            savedContentX.set(pos.x() + 3);
            savedContentY.set(pos.y() + 3);

            ModernUIBackgroundRenderer.drawRoundedBackground(
                    graphics, pose, (float) pos.x(), (float) pos.y(), width, height, state);
            RenderDebug.step("PANEL", "SDF bg buffered");

            // Flush SDF background and reset render state.
            graphics.flush();
            RenderSystem.disableDepthTest();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderDebug.step("PANEL", "render state reset after SDF");
        } catch (Exception e) {
            RenderDebug.step("PANEL", "SDF bg failed, fallback: " + e.getClass().getSimpleName());
            state.renderPanel(graphics, pos, width, height);
        }
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Ldev/obscuria/tooltips/client/TooltipState;renderFrame(Lnet/minecraft/client/gui/GuiGraphics;Lorg/joml/Vector2ic;II)V"))
    private static void redirectRenderFrame(TooltipState state, GuiGraphics graphics, Vector2ic pos, int width, int height) {
        RenderDebug.step("FRAME", "redirect — pos=(" + pos.x() + "," + pos.y() + ") size=" + width + "x" + height);
        if (!isModernUIAvailable()) {
            RenderDebug.step("FRAME", "ModernUI not available, original renderFrame");
            state.renderFrame(graphics, pos, width, height);
            return;
        }
        RenderDebug.step("FRAME", "SKIP — SDF background already buffered");
    }
}