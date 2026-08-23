package dev.waterfrog.modernobscurecompat;

import com.mojang.logging.LogUtils;
import dev.waterfrog.modernobscurecompat.autotest.AutoTestHarness;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

// Forge 1.20.1's FML requires every modId in mods.toml to have a matching
// @Mod class (NeoForge/Fabric allow entryless mods; Forge does not).
@Mod("modernobscurecompat")
public final class ModernObscureCompatForge {

    public static final Logger LOGGER = LogUtils.getLogger();

    public ModernObscureCompatForge() {
        boolean autotest = "true".equalsIgnoreCase(System.getProperty("modernobscure.autotest", "")) || "1".equals(System.getProperty("modernobscure.autotest", ""));
        try {
            java.nio.file.Files.writeString(
                    java.nio.file.Path.of("autotest-boot.txt"),
                    "constructed, property=" + autotest + ", thread=" + Thread.currentThread().getName() + "\n",
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
        if (autotest) {
            LOGGER.info("[autotest] property detected, registering harness");
            AutoTestHarness.register();
        }
    }
}
