package dev.waterfrog.modernobscurecompat.mixin;

import dev.waterfrog.modernobscurecompat.compat.CompatState;
import dev.waterfrog.modernobscurecompat.compat.ModernUITooltipGuard;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;

/**
 * Routes tooltips that obscure-tooltips can render (item tooltips carrying its
 * StackBuffer component) away from ModernUI's own tooltip renderer.
 *
 * <p>Mixin handler order at the same injection point is not controllable across
 * mods, so the ModernUI option is hidden by a non-cancellable handler (those
 * always run first) and the actual rendering happens in a cancellable handler
 * with priority 0 (Mixin runs lower-priority handlers first; ModernUI uses
 * 1 and obscure-tooltips 1000, so this runs before both).
 */
@Mixin(value = GuiGraphics.class, priority = 0)
public abstract class MixinGuiGraphics {

    @Unique
    private static Method obscureRenderMethod;
    @Unique
    private static Method clientGroupTooltipFindFirst;
    @Unique
    private static Class<?> stackBufferClass;
    @Unique
    private static boolean reflectionInitAttempted;

    @Unique
    private static void initReflection() {
        if (reflectionInitAttempted) return;
        reflectionInitAttempted = true;
        try {
            Class<?> clazz = Class.forName("dev.obscuria.tooltips.client.TooltipRenderer");
            obscureRenderMethod = clazz.getMethod("render",
                    GuiGraphics.class, Font.class, List.class,
                    int.class, int.class, ClientTooltipPositioner.class);
        } catch (Exception ignored) {
        }
        try {
            Class<?> clazz = Class.forName("dev.obscuria.fragmentum.client.ClientGroupTooltip");
            clientGroupTooltipFindFirst = clazz.getMethod("findFirst", List.class, Class.class);
            stackBufferClass = Class.forName("dev.obscuria.tooltips.client.component.StackBuffer");
        } catch (Exception ignored) {
        }
    }

    /**
     * Same check obscure-tooltips uses internally: whether the component list
     * carries a StackBuffer (item tooltips).
     */
    @Unique
    private static boolean hasStackBuffer(List<ClientTooltipComponent> components) {
        initReflection();
        if (clientGroupTooltipFindFirst == null || stackBufferClass == null) return false;
        try {
            return clientGroupTooltipFindFirst.invoke(null, components, stackBufferClass) != null;
        } catch (Exception e) {
            return false;
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

    /**
     * The main tooltip entry point used for item tooltips. Runs before ModernUI's
     * stream injector (which sits at the List.stream() invocation inside this
     * method), so hiding the option here reliably neutralizes ModernUI for item
     * tooltips regardless of mixin handler ordering (issue #2, trigger 1).
     */
    @Inject(method = "renderTooltip(Lnet/minecraft/client/gui/Font;Ljava/util/List;Ljava/util/Optional;II)V", at = @At("HEAD"))
    private void preRenderTooltip(Font font, List<? extends Component> lines,
                                  Optional<TooltipComponent> optional, int x, int y,
                                  CallbackInfo ci) {
        if (optional.isPresent() && hasStackBufferOptional(optional.get())) {
            ModernUITooltipGuard.saveAndDisable();
        }
    }

    @Unique
    private static boolean hasStackBufferOptional(Object tooltipComponent) {
        initReflection();
        if (stackBufferClass == null) return false;
        if (stackBufferClass.isInstance(tooltipComponent)) return true;
        try {
            // dev.obscuria.fragmentum.content.world.tooltip.GroupTooltip
            if (tooltipComponent.getClass().getName()
                    .equals("dev.obscuria.fragmentum.content.world.tooltip.GroupTooltip")) {
                Method components = tooltipComponent.getClass().getMethod("components");
                List<?> list = (List<?>) components.invoke(tooltipComponent);
                for (Object component : list) {
                    if (stackBufferClass.isInstance(component)) return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * Non-cancellable: always runs before every other handler at this point.
     * Hide ModernUI's tooltip option only for tooltips obscure-tooltips will
     * render, so ModernUI's own handler skips them (issue #2, trigger 1).
     * Non-item tooltips keep the user's ModernUI option untouched.
     */
    @Inject(method = "renderTooltipInternal", at = @At("HEAD"))
    private void preRenderTooltipInternal(Font font, List<ClientTooltipComponent> components,
                                          int mouseX, int mouseY, ClientTooltipPositioner positioner,
                                          CallbackInfo ci) {
        if (hasStackBuffer(components)) {
            ModernUITooltipGuard.saveAndDisable();
        }
    }

    /**
     * Cancellable: runs after ModernUI's handler (which was neutralized above
     * for item tooltips) and before obscure-tooltips' own handler.
     */
    @Inject(method = "renderTooltipInternal", at = @At("HEAD"), cancellable = true)
    private void beforeTooltipRender(Font font, List<ClientTooltipComponent> components,
                                      int mouseX, int mouseY, ClientTooltipPositioner positioner,
                                      CallbackInfo ci) {
        // Clear any leftover flag from previous render
        CompatState.setRendering(false);

        var self = (GuiGraphics) (Object) this;
        boolean handled = callObscureTooltipRenderer(self, font, components, mouseX, mouseY, positioner);

        if (handled) {
            // Restore the user's ModernUI option now: a cancelled HEAD skips the
            // body AND the TAIL injector, so the TAIL cannot be relied upon.
            ModernUITooltipGuard.restore();
            ci.cancel();
            // Keep RENDERING flag set to block obscure's own HEAD injector
            // from double-rendering. The flag will be cleared at start of next HEAD.
            return;
        }

        // obscure didn't handle → clear flag immediately so obscure's own HEAD
        // can still call render (returns false, harmless)
        CompatState.setRendering(false);

        // Not handled: restore the option. ModernUI's handler already ran and
        // saw it disabled; obscure's own handler will try to render again and
        // either cancel (leaving the restore below as the only undo — see
        // MixinGameRenderer) or fall through to the vanilla body.
        ModernUITooltipGuard.restore();
    }

    @Inject(method = "renderTooltipInternal", at = @At("TAIL"))
    private void afterTooltipRender(Font font, List<ClientTooltipComponent> components,
                                     int mouseX, int mouseY, ClientTooltipPositioner positioner,
                                     CallbackInfo ci) {
        // Safety net for the vanilla-body path.
        ModernUITooltipGuard.restore();
        CompatState.setRendering(false);
    }
}
