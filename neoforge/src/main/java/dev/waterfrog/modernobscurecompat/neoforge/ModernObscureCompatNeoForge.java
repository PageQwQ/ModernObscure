package dev.waterfrog.modernobscurecompat.neoforge;

import dev.waterfrog.modernobscurecompat.config.CompatConfigScreen;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * Registers the Cloth Config screen with NeoForge's mod-list "Config" button.
 *
 * <p>Cloth Config is optional: registration is skipped when it is not loaded,
 * so no button appears and {@link CompatConfigScreen} (which references Cloth
 * Config) is never class-loaded.
 */
@Mod(value = "modernobscurecompat", dist = Dist.CLIENT)
public class ModernObscureCompatNeoForge {

    public ModernObscureCompatNeoForge(ModContainer container) {
        if (ModList.get().isLoaded("cloth_config")) {
            container.registerExtensionPoint(IConfigScreenFactory.class,
                    (minecraft, parent) -> CompatConfigScreen.create(parent));
        }
    }
}
