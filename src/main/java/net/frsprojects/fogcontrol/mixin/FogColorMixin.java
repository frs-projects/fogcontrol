package net.frsprojects.fogcontrol.mixin;

import net.frsprojects.fogcontrol.client.ClientFogState;
import net.frsprojects.fogcontrol.core.FogState;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Custom fog color. Kept separate from FogRendererMixin because it needs a LOW priority:
 * mixins are applied in priority order and each TAIL hook is inserted just before the return,
 * so lower priority = runs earlier. Oculus (Iris) reads fogRed/Green/Blue in its own
 * setupColor TAIL hook (default priority 1000) to clear the shader pack's render targets;
 * running before it means Oculus captures our custom color instead of vanilla's.
 */
@Mixin(value = FogRenderer.class, priority = 900)
public abstract class FogColorMixin {

    // Vanilla's computed fog color; used for the shader fog color, the sky horizon blend and the clear color.
    @Shadow private static float fogRed;
    @Shadow private static float fogGreen;
    @Shadow private static float fogBlue;

    /**
     * Replaces the fog color with the custom color, after vanilla and Forge's ComputeFogColor event.
     * The custom color is treated as the daytime color and darkened for night only. It is
     * deliberately independent of weather (rain/thunder don't tint it), so it keeps matching the sky
     * color. Night darkening follows the dimension's own rule, so it doesn't glow in the dark.
     *
     * Embeddium and Distant Horizons both take their fog color from these values, so they follow.
     */
    @Inject(method = "setupColor", at = @At("TAIL"))
    private static void fogcontrol$applyCustomColor(Camera camera, float partialTick, ClientLevel level,
                                                    int renderDistanceChunks, float bossColorModifier, CallbackInfo ci) {
        FogState state = ClientFogState.effective();
        if (state == null || state.fogColorWeight() <= 0.0F) return;

        // Only where our fog override is active (not underwater, blind, in the Nether...).
        // setupColor runs just before setupFog, so this reflects the previous frame - one frame of lag at most.
        if (Float.isNaN(ClientFogState.getAppliedRenderDistance())) return;

        Vec3 color = new Vec3(state.fogR(), state.fogG(), state.fogB());

        // Day/night brightness, using the dimension's own rule (same as vanilla's biome fog color).
        float daylight = Mth.clamp(Mth.cos(level.getTimeOfDay(partialTick) * ((float) Math.PI * 2.0F)) * 2.0F + 0.5F, 0.0F, 1.0F);
        color = level.effects().getBrightnessDependentFogColor(color, daylight);

        float r = (float) color.x;
        float g = (float) color.y;
        float b = (float) color.z;

        // Blend with the vanilla color by the transition weight (1 = fully custom).
        float w = Math.min(state.fogColorWeight(), 1.0F);
        fogRed += (r - fogRed) * w;
        fogGreen += (g - fogGreen) * w;
        fogBlue += (b - fogBlue) * w;
        RenderSystem.clearColor(fogRed, fogGreen, fogBlue, 0.0F);
    }

    /**
     * levelFogColor() sends fogRed/Green/Blue to the shaders. Other mods can alter that call
     * (Dynamic Surroundings: Rebirth shifts it toward grey-white during its weather sky events).
     * While a custom fog color is shown, send our values again at the end so weather can't tint it.
     */
    @Inject(method = "levelFogColor", at = @At("TAIL"))
    private static void fogcontrol$keepCustomFogColor(CallbackInfo ci) {
        FogState state = ClientFogState.effective();
        if (state == null || state.fogColorWeight() <= 0.0F) return;
        if (Float.isNaN(ClientFogState.getAppliedRenderDistance())) return; // our fog isn't active (water, Nether...)
        RenderSystem.setShaderFogColor(fogRed, fogGreen, fogBlue);
    }
}
