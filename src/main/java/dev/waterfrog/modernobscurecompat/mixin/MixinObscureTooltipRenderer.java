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
import net.minecraft.resources.ResourceLocation;
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
    private static final ResourceLocation ITEM_SLOT = ResourceLocation.fromNamespaceAndPath("modernobscurecompat", "textures/gui/item_slot.png");

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
        RenderDebug.step("RETURN", "afterRender - final flush & AppleSkin re-render");
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
            RenderDebug.step("AFTER", "missing saved data, skip");
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
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
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
                RenderDebug.step("AFTER", "rendering AppleSkin at (" + componentX + "," + componentY + ")");
                try {
                    graphics.pose().pushPose();
                    graphics.pose().translate(0f, 0f, 400f);
                    Method drawItems = comp.getClass().getMethod("method_32666", Font.class, int.class, int.class, GuiGraphics.class);
                    drawItems.invoke(comp, fnt, componentX, componentY, graphics);
                    graphics.pose().popPose();
                    graphics.flush();
                    RenderDebug.step("AFTER", "AppleSkin rendered successfully");
                } catch (Exception e) {
                    RenderDebug.step("AFTER", "AppleSkin failed: " + e.getClass().getSimpleName() + ": " + e.getMessage());
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
        RenderDebug.step("PANEL", "redirect — pos=(" + pos.x() + "," + pos.y() + ") size=" + width + "x" + height);
        if (!isModernUIAvailable()) {
            RenderDebug.step("PANEL", "ModernUI not available, original renderPanel");
            state.renderPanel(graphics, pos, width, height);
            return;
        }
        try {
            RenderDebug.step("PANEL", "buffer SDF bg via ModernUI");
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
            RenderDebug.step("PANEL", "SDF bg buffered");

            // Flush SDF background and reset render state.
            graphics.flush();
            RenderSystem.disableDepthTest();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.depthFunc(519); // GL_ALWAYS

            // Draw item_slot texture on top of SDF background, below components.
            // obscure's ColorRectSlot draws with very low alpha (~12%) on the
            // SDF background, making it nearly invisible. Our item_slot.png
            // provides a clear visible slot border.
            // The slot is rendered by HeaderComponent at (pos.x+margin, pos.y+margin)
            // with size 20x20. The item icon (16x16) is centered at (pos.x+5, pos.y+5).
            // Draw our slot texture at the same position with 1px padding around the icon.
            RenderDebug.step("PANEL", "drawing item_slot texture");
            int slotX = pos.x() + 4;
            int slotY = pos.y() + 4;
            graphics.blit(ITEM_SLOT, slotX, slotY, 0, 0, 18, 18, 16, 16);
            RenderDebug.step("PANEL", "item_slot texture drawn at (" + slotX + "," + slotY + ")");
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