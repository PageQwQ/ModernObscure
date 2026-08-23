package dev.waterfrog.modernobscurecompat.autotest;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.level.GameRules;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;

/**
 * DEV-ONLY automated test harness, active only when the JVM property
 * {@code -Dmodernobscure.autotest=true} is set.
 *
 * Flow: title screen -> programmatically create+join a flat-ish world ->
 * open a blank screen rendering several tooltips every frame (plain item /
 * appleskin food / TACZ gun) -> dump framebuffer screenshots into
 * {@code autotest/} -> quit.
 */
public final class AutoTestHarness {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String WORLD_NAME = "AutoTestWorld";
    private static int ticks;
    private static boolean worldRequested;

    public static void register() {
        MinecraftForge.EVENT_BUS.register(AutoTestHarness.class);
    }

    private AutoTestHarness() {
    }

    @SubscribeEvent
    static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ticks++;

        if (!worldRequested) {
            if (ticks < 80 || mc.screen == null || mc.level != null) return;
            LOGGER.info("[autotest] requesting fresh world creation");
            worldRequested = true;
            try {
                mc.createWorldOpenFlows().createFreshLevel(
                        WORLD_NAME,
                        new LevelSettings(WORLD_NAME, GameType.CREATIVE, false, Difficulty.PEACEFUL,
                                true, new GameRules(), WorldDataConfiguration.DEFAULT),
                        new WorldOptions(12345L, false, false),
                        registryAccess -> registryAccess.registryOrThrow(Registries.WORLD_PRESET)
                                .getHolderOrThrow(WorldPresets.NORMAL).value().createWorldDimensions());
            } catch (Throwable t) {
                LOGGER.error("[autotest] world creation failed", t);
            }
            return;
        }

        // wait for the player to be in-world, then settle briefly
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
            this.renderBackground(graphics);
            frames++;

            // Control: plain vanilla item tooltip
            graphics.renderTooltip(this.font, new ItemStack(Items.DIAMOND), 30, 30);
            // AppleSkin food overlay scenario
            graphics.renderTooltip(this.font, new ItemStack(Items.COOKED_BEEF), 30, 190);
            // TACZ gun tooltip (custom ClientTooltipComponent + ammo icon via renderFakeItem)
            ItemStack gun = buildTaczGun();
            if (gun != null) {
                graphics.renderTooltip(this.font, gun, 280, 50);
            }
            // Shulker Box Tooltip scenario (container preview grid rendered via renderImage)
            ItemStack shulker = buildFilledShulker();
            if (shulker != null) {
                graphics.renderTooltip(this.font, shulker, 30, 330);
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
            } catch (IOException e) {
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
                        .invoke(builder, new ResourceLocation("tacz", "kar98"));
                builder = builderClass.getMethod("setAmmoInBarrel", boolean.class).invoke(builder, true);
                return (ItemStack) builderClass.getMethod("forceBuild").invoke(builder);
            } catch (Throwable t) {
                LOGGER.warn("[autotest] failed to build tacz gun stack", t);
                return null;
            }
        }

        /** Shulker box with a few items inside, for container-preview tooltip mods. */
        static ItemStack buildFilledShulker() {
            try {
                net.minecraft.nbt.CompoundTag item0 = new net.minecraft.nbt.CompoundTag();
                item0.putByte("Slot", (byte) 0);
                item0.putString("id", "minecraft:diamond");
                item0.putByte("Count", (byte) 12);
                net.minecraft.nbt.CompoundTag item1 = new net.minecraft.nbt.CompoundTag();
                item1.putByte("Slot", (byte) 1);
                item1.putString("id", "minecraft:golden_apple");
                item1.putByte("Count", (byte) 5);
                net.minecraft.nbt.ListTag items = new net.minecraft.nbt.ListTag();
                items.add(item0);
                items.add(item1);
                net.minecraft.nbt.CompoundTag blockEntity = new net.minecraft.nbt.CompoundTag();
                blockEntity.put("Items", items);
                net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
                tag.put("BlockEntityTag", blockEntity);
                ItemStack shulker = new ItemStack(net.minecraft.world.item.Items.SHULKER_BOX);
                shulker.setTag(tag);
                shulker.setHoverName(Component.literal("Filled Shulker"));
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
