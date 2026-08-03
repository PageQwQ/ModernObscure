package dev.waterfrog.modernobscurecompat.mixin;

import dev.obscuria.fragmentum.config.ConfigBuilder;
import dev.obscuria.fragmentum.config.ConfigValue;
import dev.obscuria.tooltips.config.ClientConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ClientConfig.class)
public abstract class MixinClientConfig {

    // obscure-tooltips 4.2.2 flipped toolPreviewEnabled's default from true (3.10.0)
    // to false, so fresh 1.21.1 installs silently lost the rotating tool preview that
    // worked in 1.20.1. Restore the old default; an explicit user setting still wins.
    @Redirect(method = "<clinit>",
            at = @At(value = "INVOKE",
                    target = "Ldev/obscuria/fragmentum/config/ConfigBuilder;defineBoolean(Ljava/lang/String;Z)Ldev/obscuria/fragmentum/config/ConfigValue;"))
    private static ConfigValue<Boolean> restoreToolPreviewDefault(ConfigBuilder builder,
                                                                  String key, boolean defaultValue) {
        if (key.equals("toolPreviewEnabled")) {
            defaultValue = true;
        }
        return builder.defineBoolean(key, defaultValue);
    }
}
