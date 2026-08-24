package dev.waterfrog.modernobscurecompat.autotest;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.slf4j.Logger;

import java.io.File;

/**
 * DEV-ONLY automated test harness, active only when the JVM property
 * {@code -Dmodernobscure.autotest=true} (or =1) is set. Loader-agnostic
 * (driven by MixinMinecraftAutotest): title screen -> programmatically
 * create+join a world -> open a blank screen rendering several tooltips
 * every frame (plain item / appleskin food / shulker container / TACZ gun
 * if present) -> dump framebuffer screenshots into {@code autotest/} ->
 * quit.
 */
public final class AutoTestHarness {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String WORLD_NAME = "AutoTestWorld";
    private static int ticks;
    private static boolean worldRequested;

    private AutoTestHarness() {
    }

    public static boolean isEnabled() {
        String v = System.getProperty("modernobscure.autotest", "");
        if ("true".equalsIgnoreCase(v) || "1".equals(v)) return true;
        // Marker-file fallback so the harness can be enabled in dev runs
        // (--runClient forks a new JVM; passing system properties through is awkward).
        return new File(".autotest-enable").canRead();
    }

    /** Called from Minecraft.tick via mixin. */
    public static void onClientTick(Minecraft mc) {
        if (!isEnabled()) return;
        ticks++;

        if (!worldRequested) {
            if (ticks < 80 || mc.screen == null || mc.level != null || mc.player != null) return;
            LOGGER.info("[autotest] requesting fresh world creation");
            worldRequested = true;
            try {
                mc.createWorldOpenFlows().createFreshLevel(
                        WORLD_NAME,
                        new LevelSettings(WORLD_NAME, GameType.CREATIVE, false, Difficulty.PEACEFUL,
                                true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(12345L, false, false),
                        registryAccess -> registryAccess.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.NORMAL).value().createWorldDimensions(),
                        null);
            } catch (Throwable t) {
                LOGGER.error("[autotest] world creation failed", t);
            }
            return;
        }

        MinecraftServer server = mc.getSingleplayerServer();
        if (mc.player == null || mc.level == null || server == null || !server.isRunning()) return;
        if (!(mc.screen instanceof AutoTestScreen)) {
            LOGGER.info("[autotest] opening test screen (tick {}, screen {})", ticks,
                    mc.screen == null ? "null" : mc.screen.getClass().getName());
            mc.setScreen(new AutoTestScreen());
        }
    }

    /** Blank screen that continuously renders the test tooltips and screenshots them. */
    static final class AutoTestScreen extends Screen {

        private static final int CAPTURE_INTERVAL = 20;
        private int frames;
        private int captures;

        AutoTestScreen() {
            super(Component.literal("AutoTest"));
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            this.renderBackground(graphics, mouseX, mouseY, partialTick);
            frames++;

            // Control: plain vanilla item tooltip
            graphics.renderTooltip(this.font, new ItemStack(Items.DIAMOND), 30, 30);
            // AppleSkin food overlay scenario
            graphics.renderTooltip(this.font, new ItemStack(Items.COOKED_BEEF), 30, 190);
            // Shulker Box Tooltip / Peek scenario (container preview grid)
            graphics.renderTooltip(this.font, buildFilledShulker(), 30, 330);
            // TACZ gun tooltip (custom ClientTooltipComponent + ammo icon via renderFakeItem)
            ItemStack gun = buildTaczGun();
            if (gun != null) {
                graphics.renderTooltip(this.font, gun, 280, 50);
            }

            if (frames >= CAPTURE_INTERVAL && captures < 4) {
                capture();
                frames = 0;
            }
        }

        private void capture() {
            File dir = new File("autotest");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "shot-" + captures + ".png");
            try (var image = Screenshot.takeScreenshot(this.minecraft.getMainRenderTarget())) {
                image.writeToFile(file);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            captures++;
            LOGGER.info("[autotest] captured {} -> {} bytes", file.getName(), file.length());
            if (captures >= 4) {
                LOGGER.info("[autotest] done, stopping client");
                this.minecraft.stop();
            }
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        static ItemStack buildTaczGun() {
            try {
                Class builderClass = Class.forName("com.tacz.guns.api.item.builder.GunItemBuilder");
                Object builder = builderClass.getMethod("create").invoke(null);
                builder = builderClass.getMethod("setId", ResourceLocation.class)
                        .invoke(builder, ResourceLocation.fromNamespaceAndPath("tacz", "kar98"));
                builder = builderClass.getMethod("setAmmoInBarrel", boolean.class).invoke(builder, true);
                return (ItemStack) builderClass.getMethod("forceBuild").invoke(builder);
            } catch (Throwable t) {
                LOGGER.info("[autotest] tacz not present ({})", t.toString());
                return null;
            }
        }

        /** Shulker box with a few items inside, for container-preview tooltip mods. */
        static ItemStack buildFilledShulker() {
            try {
                ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
                shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(java.util.List.of(
                        new ItemStack(Items.DIAMOND, 12),
                        new ItemStack(Items.GOLDEN_APPLE, 5))));
                shulker.set(DataComponents.CUSTOM_NAME, Component.literal("Filled Shulker"));
                return shulker;
            } catch (Throwable t) {
                LOGGER.warn("[autotest] failed to build shulker stack", t);
                return null;
            }
        }

        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
