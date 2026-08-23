package dev.waterfrog.modernobscurecompat.mixin;

import dev.waterfrog.modernobscurecompat.compat.CompatState;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(value = GuiGraphics.class, priority = 2000)
public abstract class MixinGuiGraphics {

    @Unique
    private static Field sTooltipField;
    @Unique
    private static Method obscureRenderMethod;
    @Unique
    private static boolean reflectionInitAttempted;

    @Unique
    private static void initReflection() {
        if (reflectionInitAttempted) return;
        reflectionInitAttempted = true;
        try {
            Class<?> clazz = Class.forName("icyllis.modernui.mc.TooltipRenderer");
            sTooltipField = clazz.getDeclaredField("sTooltip");
            sTooltipField.setAccessible(true);
        } catch (Exception ignored) {
        }
        try {
            Class<?> clazz = Class.forName("dev.obscuria.tooltips.client.TooltipRenderer");
            obscureRenderMethod = clazz.getMethod("render",
                    GuiGraphics.class, Font.class, List.class,
                    int.class, int.class, ClientTooltipPositioner.class);
        } catch (Exception ignored) {
        }
    }

    @Unique
    private static boolean disableModernUITooltip() {
        initReflection();
        if (sTooltipField == null) return false;
        try {
            sTooltipField.setBoolean(null, false);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Unique
    private static void enableModernUITooltip() {
        if (sTooltipField == null) return;
        try {
            sTooltipField.setBoolean(null, true);
        } catch (Exception ignored) {
        }
    }

    @Unique
    private static boolean callObscureTooltipRenderer(GuiGraphics self, Font font,
                                                       List<ClientTooltipComponent> components,
                                                       int mouseX, int mouseY,
                                                       ClientTooltipPositioner positioner) {
        initReflection();
        if (obscureRenderMethod == null) return false;
        try {
            return (boolean) obscureRenderMethod.invoke(null, self, font, components, mouseX, mouseY, positioner);
        } catch (Exception e) {
            return false;
        }
    }

    // Fabric runtime is intermediary; GuiGraphics.renderTooltipInternal is
    // method_51435 there (loom-generated refmap of the old common mixin
    // confirmed this mapping). remap=false since this file is platform-specific
    // and the name is already the runtime one.
    @Inject(method = "method_51435", at = @At("HEAD"), cancellable = true, remap = false)
    private void beforeTooltipRender(Font font, List<ClientTooltipComponent> components,
                                      int mouseX, int mouseY, ClientTooltipPositioner positioner,
                                      CallbackInfo ci) {
        // Clear any leftover flag from previous render
        CompatState.setRendering(false);

        // Route ALL tooltips (including grouped ones like TACZ guns, whose
        // getTooltipImage is grouped with StackBuffer) through obscure's renderer
        // so every item keeps the same panel style. Grouped tooltips may render
        // item icons via GuiGraphics.renderItem, which flushes the buffer mid-way
        // with text still pending; MixinObscureTooltipRenderer pushes the fog out
        // before the component loop so that mid-loop flush draws the glyphs
        // correctly instead of fogging them to black.
        boolean modernUIDisabled = disableModernUITooltip();
        var self = (GuiGraphics) (Object) this;
        boolean handled = callObscureTooltipRenderer(self, font, components, mouseX, mouseY, positioner);

        if (handled) {
            ci.cancel();
            // Keep RENDERING flag set to block obscure's own HEAD injector
            // from double-rendering. The flag will be cleared at start of next HEAD.
            return;
        }
        // obscure didn't handle → clear flag immediately so obscure's own HEAD
        // can still call render (returns false, harmless)
        CompatState.setRendering(false);

        if (modernUIDisabled) {
            enableModernUITooltip();
        }
    }

    @Inject(method = "method_51435", at = @At("TAIL"), remap = false)
    private void afterTooltipRender(Font font, List<ClientTooltipComponent> components,
                                     int mouseX, int mouseY, ClientTooltipPositioner positioner,
                                     CallbackInfo ci) {
        enableModernUITooltip();
        CompatState.setRendering(false);
    }
}
