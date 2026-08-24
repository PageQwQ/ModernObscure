package dev.waterfrog.modernobscurecompat.mixin;

import dev.waterfrog.modernobscurecompat.autotest.AutoTestHarness;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * DEV-ONLY: drives {@link AutoTestHarness} from the client tick. Inert unless
 * -Dmodernobscure.autotest=true is set (the handler exits immediately).
 * Minecraft.tick has different runtime names per environment; exactly one of
 * these variants can match (require = 0 keeps the others silent).
 */
@Mixin(value = Minecraft.class, priority = 500)
public abstract class MixinMinecraftAutotest {

    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void autotestTickMojmap(CallbackInfo ci) {
        AutoTestHarness.onClientTick((Minecraft) (Object) this);
    }

    @Inject(method = "method_1574", at = @At("TAIL"), remap = false, require = 0)
    private void autotestTickIntermediary(CallbackInfo ci) {
        AutoTestHarness.onClientTick((Minecraft) (Object) this);
    }
}
