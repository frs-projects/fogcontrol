package net.frsprojects.fogcontrol.mixin;

import net.frsprojects.fogcontrol.client.ClientFogState;
import net.frsprojects.fogcontrol.core.FogMath;
import net.frsprojects.fogcontrol.core.FogState;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs after vanilla (and Forge's RenderFog event) have set up fog, and replaces the
 * terrain fog start/end. Embeddium reads these same values from RenderSystem for its
 * chunk shaders, so this works with both the vanilla renderer and Embeddium.
 */
@Mixin(value = FogRenderer.class, priority = 1500) // applied after default-priority mixins, so our RETURN hook runs last
public abstract class FogRendererMixin {

    @Inject(method = "setupFog", at = @At("TAIL"))
    private static void fogcontrol$applyCustomFog(Camera camera, FogRenderer.FogMode fogMode, float renderDistance,
                                                  boolean thickFog, float partialTick, CallbackInfo ci) {
        // Only normal terrain fog; the sky keeps its vanilla horizon blend.
        if (fogMode != FogRenderer.FogMode.FOG_TERRAIN) return;

        // Assume "not applied" until every check passes (read by the Distant Horizons compat).
        ClientFogState.setAppliedRenderDistance(Float.NaN);

        FogState state = ClientFogState.effective();
        if (state == null) return; // server doesn't have the mod

        // Leave water, lava and powder snow fog alone.
        if (camera.getFluidInCamera() != FogType.NONE) return;

        // Leave Nether / boss fog vanilla.
        if (thickFog) return;

        // Leave blindness / darkness alone.
        Entity entity = camera.getEntity();
        if (entity instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS))) {
            return;
        }

        FogMath.FogRange range = FogMath.compute(renderDistance, state.minPercent(), state.maxPercent());
        RenderSystem.setShaderFogStart(range.start());
        RenderSystem.setShaderFogEnd(range.end());
        ClientFogState.setAppliedRenderDistance(renderDistance);
    }

    /**
     * Vanilla tints the fog toward the sunrise/sunset color when you look toward the sun.
     * That's barely noticeable with vanilla edge-only fog, but with fog visible everywhere
     * the whole view changes color. Returning null here skips that tint (the same thing
     * vanilla does when there's no sunrise). The sunset glow drawn in the sky itself is
     * rendered separately and is not affected.
     */
    @Redirect(method = "setupColor",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/renderer/DimensionSpecialEffects;getSunriseColor(FF)[F"))
    private static float[] fogcontrol$removeSunsetFogTint(DimensionSpecialEffects effects, float timeOfDay, float partialTick) {
        if (ClientFogState.get() != null) {
            return null; // server runs Fog Control: no sunset tint on the fog
        }
        return effects.getSunriseColor(timeOfDay, partialTick);
    }
}
