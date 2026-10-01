package dev.waterfrog.modernobscurecompat.fabric;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.waterfrog.modernobscurecompat.config.CompatConfigScreen;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.screens.Screen;

/**
 * Mod Menu entrypoint (only invoked when Mod Menu is installed). The config
 * screen itself requires Cloth Config, so when that is missing the factory
 * yields {@code null} and Mod Menu hides the config button.
 *
 * <p>Both dependencies are optional; this class is only ever loaded through
 * Mod Menu's entrypoint namespace.
 */
public class ModMenuIntegration implements ModMenuApi {

    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        if (!FabricLoader.getInstance().isModLoaded("cloth-config")) {
            return (ConfigScreenFactory<Screen>) parent -> null;
        }
        return (ConfigScreenFactory<Screen>) CompatConfigScreen::create;
    }
}
