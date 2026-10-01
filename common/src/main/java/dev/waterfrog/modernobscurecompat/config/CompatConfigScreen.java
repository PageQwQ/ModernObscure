package dev.waterfrog.modernobscurecompat.config;

import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Cloth Config screen for the compat mod. Only ever loaded when Cloth Config is
 * present: the loader integrations guard on the mod being loaded/invoked before
 * touching this class, so the dependency stays optional.
 */
public final class CompatConfigScreen {

    private CompatConfigScreen() {
    }

    public static Screen create(Screen parent) {
        ConfigBuilder builder = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(Component.translatable("modernobscurecompat.config.title"))
                .setSavingRunnable(CompatClientConfig::save);

        ConfigEntryBuilder entries = builder.entryBuilder();
        ConfigCategory general = builder.getOrCreateCategory(
                Component.translatable("modernobscurecompat.config.category.general"));

        general.addEntry(entries.startBooleanToggle(
                        Component.translatable("modernobscurecompat.config.enabled"),
                        CompatClientConfig.isEnabled())
                .setDefaultValue(true)
                .setTooltip(Component.translatable("modernobscurecompat.config.enabled.tooltip"))
                .setSaveConsumer(CompatClientConfig::setEnabled)
                .build());

        general.addEntry(entries.startBooleanToggle(
                        Component.translatable("modernobscurecompat.config.smoothMovement"),
                        CompatClientConfig.isSmoothMovement())
                .setDefaultValue(true)
                .setTooltip(Component.translatable("modernobscurecompat.config.smoothMovement.tooltip"))
                .setSaveConsumer(CompatClientConfig::setSmoothMovement)
                .build());

        general.addEntry(entries.startDoubleField(
                        Component.translatable("modernobscurecompat.config.movementSpeed"),
                        CompatClientConfig.getMovementSpeed())
                .setDefaultValue(2.0)
                .setMin(0.1)
                .setMax(10.0)
                .setTooltip(Component.translatable("modernobscurecompat.config.movementSpeed.tooltip"))
                .setSaveConsumer(value -> CompatClientConfig.setMovementSpeed(value))
                .build());

        general.addEntry(entries.startDoubleField(
                        Component.translatable("modernobscurecompat.config.snapDistance"),
                        CompatClientConfig.getSnapDistance())
                .setDefaultValue(48.0)
                .setMin(0.0)
                .setMax(512.0)
                .setTooltip(Component.translatable("modernobscurecompat.config.snapDistance.tooltip"))
                .setSaveConsumer(value -> CompatClientConfig.setSnapDistance(value))
                .build());

        general.addEntry(entries.startBooleanToggle(
                        Component.translatable("modernobscurecompat.config.smoothSize"),
                        CompatClientConfig.isSmoothSize())
                .setDefaultValue(true)
                .setTooltip(Component.translatable("modernobscurecompat.config.smoothSize.tooltip"))
                .setSaveConsumer(CompatClientConfig::setSmoothSize)
                .build());

        general.addEntry(entries.startDoubleField(
                        Component.translatable("modernobscurecompat.config.sizeSpeed"),
                        CompatClientConfig.getSizeSpeed())
                .setDefaultValue(1.5)
                .setMin(0.1)
                .setMax(10.0)
                .setTooltip(Component.translatable("modernobscurecompat.config.sizeSpeed.tooltip"))
                .setSaveConsumer(value -> CompatClientConfig.setSizeSpeed(value))
                .build());

        general.addEntry(entries.startDoubleField(
                        Component.translatable("modernobscurecompat.config.startScale"),
                        CompatClientConfig.getStartScale())
                .setDefaultValue(0.9)
                .setMin(0.1)
                .setMax(1.0)
                .setTooltip(Component.translatable("modernobscurecompat.config.startScale.tooltip"))
                .setSaveConsumer(value -> CompatClientConfig.setStartScale(value))
                .build());

        return builder.build();
    }
}
