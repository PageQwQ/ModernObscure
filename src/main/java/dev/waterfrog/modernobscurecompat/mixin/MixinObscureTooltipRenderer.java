package dev.waterfrog.modernobscurecompat.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.obscuria.tooltips.client.TooltipRenderer;
import dev.obscuria.tooltips.client.TooltipState;
import dev.waterfrog.modernobscurecompat.compat.CompatState;
import dev.waterfrog.modernobscurecompat.compat.ModernUIBackgroundRenderer;
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
    private static final ThreadLocal<Integer> savedMargin = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<Integer> savedPosY = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<Integer> savedPosX = new ThreadLocal<>();
    @Unique
    private static final ThreadLocal<Integer> savedHeight = new ThreadLocal<>();

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
        if (CompatState.isRendering()) {
            cir.setReturnValue(false);
            return;
        }
        CompatState.setRendering(true);
        savedComponents.set(components);
        savedFont.set(font);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private static void afterRender(GuiGraphics graphics, Font font,
                                    List<ClientTooltipComponent> components,
                                    int mouseX, int mouseY,
                                    ClientTooltipPositioner positioner,
                                    CallbackInfoReturnable<Boolean> cir) {
        // Buffered slot/icon geometry is only drawn at this flush. Epic styles disable
        // blend (RayGlow), so re-enable it here or the semi-transparent item_slot
        // texture would be flushed opaque white.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.flush();

        // Re-render AppleSkin FoodOverlay at the correct position.
        // The component loop in obscure's render() handles the renderImage call,
        // but the SDF shader may corrupt GL state. We re-render here with clean state.
        List<ClientTooltipComponent> comps = savedComponents.get();
        Font fnt = savedFont.get();
        Integer margin = savedMargin.get();
        Integer posX = savedPosX.get();
        Integer posY = savedPosY.get();
        Integer height = savedHeight.get();
        if (comps == null || fnt == null || margin == null || posX == null || posY == null || height == null) {
            savedComponents.remove();
            savedFont.remove();
            savedMargin.remove();
            savedPosX.remove();
            savedPosY.remove();
            savedHeight.remove();
            return;
        }

        // Clean state for AppleSkin
        RenderSystem.disableDepthTest();
        RenderSystem.depthFunc(519); // GL_ALWAYS

        // Find AppleSkin component and calculate its Y position at the bottom of content.
        // In obscure's render():
        //   height = 2*margin + contentHeight - 2  (contentHeight = sum of all comp heights)
        //   component rendering starts at: pos.y + margin
        //   last component Y = pos.y + margin + contentHeight - lastCompHeight
        //                    = pos.y + height - margin + 2 - lastCompHeight
        for (ClientTooltipComponent comp : comps) {
            if (comp.getClass().getName().equals("squeek.appleskin.client.TooltipOverlayHandler$FoodOverlay")) {
                int componentX = margin + posX;
                int componentY = posY + height - margin + 2 - comp.getHeight();
                try {
                    graphics.pose().pushPose();
                    graphics.pose().translate(0f, 0f, 400f);
                    Method drawItems = comp.getClass().getMethod("method_32666", Font.class, int.class, int.class, GuiGraphics.class);
                    drawItems.invoke(comp, fnt, componentX, componentY, graphics);
                    graphics.pose().popPose();
                    graphics.flush();
                } catch (Exception ignored) {
                }
                break;
            }
        }

        savedComponents.remove();
        savedFont.remove();
        savedMargin.remove();
        savedPosX.remove();
        savedPosY.remove();
        savedHeight.remove();
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Ldev/obscuria/tooltips/client/TooltipState;renderPanel(Lnet/minecraft/client/gui/GuiGraphics;Lorg/joml/Vector2ic;II)V"))
    private static void redirectRenderPanel(TooltipState state, GuiGraphics graphics, Vector2ic pos, int width, int height) {
        if (!isModernUIAvailable()) {
            state.renderPanel(graphics, pos, width, height);
            return;
        }
        try {
            Matrix4f pose = graphics.pose().last().pose();
            // Save position for AppleSkin rendering. The content starts at
            // (pos.x + margin, pos.y + margin) where margin = ClientConfig.CONTENT_MARGIN.
            // We save pos.x/y and margin separately for position calculation.
            savedPosX.set(pos.x());
            savedPosY.set(pos.y());
            savedHeight.set(height);
            int margin = 3; // ClientConfig.CONTENT_MARGIN default
            savedMargin.set(margin);

            ModernUIBackgroundRenderer.drawRoundedBackground(
                    graphics, pose, (float) pos.x(), (float) pos.y(), width, height, state);

            // Flush SDF background and reset render state.
            graphics.flush();
            RenderSystem.disableDepthTest();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthFunc(519); // GL_ALWAYS
        } catch (Exception e) {
            state.renderPanel(graphics, pos, width, height);
        }
    }

    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Ldev/obscuria/tooltips/client/TooltipState;renderFrame(Lnet/minecraft/client/gui/GuiGraphics;Lorg/joml/Vector2ic;II)V"))
    private static void redirectRenderFrame(TooltipState state, GuiGraphics graphics, Vector2ic pos, int width, int height) {
        if (!isModernUIAvailable()) {
            state.renderFrame(graphics, pos, width, height);
            return;
        }
        // SKIP — SDF background already buffered with border included
    }
}