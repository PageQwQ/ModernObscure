package dev.waterfrog.modernobscurecompat.compat;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Reflection helpers for detecting and rendering obscure-tooltips tooltips,
 * without a hard runtime dependency on it. Shared by the loader-specific
 * {@code GuiGraphics} mixins.
 */
public final class ObscureTooltips {

    private ObscureTooltips() {
    }

    private static Method clientGroupTooltipFindFirst;
    private static Class<?> stackBufferClass;
    private static boolean reflectionInitAttempted;

    private static Method renderMethod;
    private static boolean renderInitAttempted;

    private static void initReflection() {
        if (reflectionInitAttempted) return;
        reflectionInitAttempted = true;
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
    public static boolean hasStackBuffer(List<ClientTooltipComponent> components) {
        initReflection();
        if (clientGroupTooltipFindFirst == null || stackBufferClass == null) return false;
        try {
            return clientGroupTooltipFindFirst.invoke(null, components, stackBufferClass) != null;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean hasStackBufferOptional(Object tooltipComponent) {
        initReflection();
        if (stackBufferClass == null) return false;
        if (stackBufferClass.isInstance(tooltipComponent)) return true;
        try {
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

    private static void initRender() {
        if (renderInitAttempted) return;
        renderInitAttempted = true;
        try {
            Class<?> clazz = Class.forName("dev.obscuria.tooltips.client.TooltipRenderer");
            renderMethod = clazz.getMethod("render",
                    GuiGraphics.class, Font.class, List.class,
                    int.class, int.class, ClientTooltipPositioner.class);
        } catch (Exception ignored) {
        }
    }

    /**
     * Invokes obscure-tooltips' own renderer directly. Doing it from the
     * priority-0 HEAD injector guarantees it runs before ModernUI's handler,
     * regardless of mixin application order.
     *
     * @return {@code true} if obscure-tooltips handled the tooltip.
     */
    public static boolean render(GuiGraphics graphics, Font font,
                                 List<ClientTooltipComponent> components,
                                 int mouseX, int mouseY,
                                 ClientTooltipPositioner positioner) {
        initRender();
        if (renderMethod == null) return false;
        try {
            return (boolean) renderMethod.invoke(null, graphics, font, components, mouseX, mouseY, positioner);
        } catch (Exception e) {
            return false;
        }
    }
}
