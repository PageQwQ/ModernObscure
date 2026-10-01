package dev.waterfrog.modernobscurecompat;

import dev.waterfrog.modernobscurecompat.config.CompatConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.function.Function;

// Forge 1.20.1's FML requires every modId in mods.toml to have a matching
// @Mod class (NeoForge/Fabric allow entryless mods; Forge does not).
@Mod("modernobscurecompat")
public final class ModernObscureCompatForge {

    public ModernObscureCompatForge() {
        // Cloth Config is optional: only register the config button when it is
        // loaded, so CompatConfigScreen (which references Cloth Config) is never
        // class-loaded otherwise.
        if (FMLEnvironment.dist == Dist.CLIENT && ModList.get().isLoaded("cloth_config")) {
            registerConfigScreen();
        }
    }

    /**
     * Registers the Cloth Config screen through Forge's {@code ConfigScreenFactory}
     * extension point.
     *
     * <p>The constructor is typed {@code Function<Screen, Screen>} /
     * {@code BiFunction<Minecraft, Screen, Screen>} in Forge itself, but Loom's
     * merged Forge jar ships a corrupted generic signature for it — javac sees
     * {@code Function<AnyOfCondition, AnyOfCondition>} and
     * {@code BiFunction<RuleTest, AnyOfCondition, AnyOfCondition>} — so a typed
     * lambda cannot be compiled against it. The erased constructor is still
     * {@code Function<Screen, Screen>} at runtime, so the raw type is passed here
     * and the single-argument overload is selected at runtime as usual.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void registerConfigScreen() {
        ModLoadingContext.get().registerExtensionPoint(
                ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (Function) (Function<Screen, Screen>) CompatConfigScreen::create));
    }
}
