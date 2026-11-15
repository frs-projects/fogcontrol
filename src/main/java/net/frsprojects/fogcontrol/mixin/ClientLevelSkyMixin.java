package net.frsprojects.fogcontrol.mixin;

import net.frsprojects.fogcontrol.client.ClientFogState;
import net.frsprojects.fogcontrol.core.FogState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Custom sky color. Vanilla's ClientLevel.getSkyColor takes the biome sky color and applies
 * time-of-day brightness, rain, thunder and lightning-flash. Starting from the custom color we
 * apply only the time-of-day brightness (so it doesn't glow at night) and lightning flashes.
 * Rain and thunder deliberately don't change it, so sky and fog color stay matched in any weather.
 *
 * Hooking the return value (instead of redirecting the biome lookup inside) avoids conflicts with
 * other mods that change how that method samples biomes.
 *
 * Everything that asks for the sky color follows: the vanilla sky dome, the fog's sky tint,
 * Distant Horizons (in sky-color fog mode) and shader packs via Oculus' "skyColor" uniform.
 *
 * Priority 800 matters: when an @Inject at RETURN calls setReturnValue, the method returns right
 * there and RETURN hooks applied after it never run. Mixins are applied lowest priority first.
 * Dynamic Surroundings: Rebirth hooks getSkyColor at RETURN with priority 900 and always sets its
 * own (weather-smoothed) value, which used to cut this hook off. Running before it means:
 * custom sky on -> our color is returned and DSR's override is skipped;
 * custom sky off -> we don't touch anything and DSR works exactly as before.
 */
@Mixin(value = ClientLevel.class, priority = 800)
public abstract class ClientLevelSkyMixin {

    @Shadow private int skyFlashTime;

    @Inject(method = "getSkyColor", at = @At("RETURN"), cancellable = true)
    private void fogcontrol$applyCustomSkyColor(Vec3 pos, float partialTick, CallbackInfoReturnable<Vec3> cir) {
        FogState state = ClientFogState.effective();
        if (state == null || state.skyColorWeight() <= 0.0F) return;

        ClientLevel level = (ClientLevel) (Object) this;

        // Time of day (same formula as vanilla)
        float daylight = Mth.clamp(Mth.cos(level.getTimeOfDay(partialTick) * ((float) Math.PI * 2.0F)) * 2.0F + 0.5F, 0.0F, 1.0F);
        float r = state.skyR() * daylight;
        float g = state.skyG() * daylight;
        float b = state.skyB() * daylight;

        // Lightning flash (respects the "Hide Lightning Flashes" accessibility option)
        int flash = Minecraft.getInstance().options.hideLightningFlash().get() ? 0 : this.skyFlashTime;
        if (flash > 0) {
            float f = Math.min((float) flash - partialTick, 1.0F) * 0.45F;
            r = r * (1.0F - f) + 0.8F * f;
            g = g * (1.0F - f) + 0.8F * f;
            b = b * (1.0F - f) + 1.0F * f;
        }

        // Blend with the vanilla sky color by the transition weight (1 = fully custom).
        Vec3 vanilla = cir.getReturnValue();
        double w = Math.min(state.skyColorWeight(), 1.0F);
        cir.setReturnValue(new Vec3(
                vanilla.x + (r - vanilla.x) * w,
                vanilla.y + (g - vanilla.y) * w,
                vanilla.z + (b - vanilla.z) * w));
    }
}
