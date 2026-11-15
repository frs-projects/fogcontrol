package net.frsprojects.fogcontrol.compat;

import net.frsprojects.fogcontrol.FogControl;
import net.frsprojects.fogcontrol.client.ClientFogState;
import net.frsprojects.fogcontrol.core.FogState;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfig;
import com.seibel.distanthorizons.api.interfaces.config.IDhApiConfigValue;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeFogRenderEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiCancelableEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiFogRenderParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiMutableFogRenderParam;

/**
 * Distant Horizons integration. Only loaded when DH is installed (see FogControl).
 *
 * 1. DH hides vanilla terrain fog by default. While connected to a Fog Control server we set DH's
 *    "enableVanillaFog" through the API. API values are in-memory overrides: the player's DH config
 *    file is never changed, and the override is cleared on disconnect.
 *
 * 2. Every frame, before DH draws its fog, we set DH's far-fog minimum thickness to our vanilla
 *    maximum, and make sure DH's fog doesn't start before the vanilla render distance edge.
 *    DH far fog = min + (max - min) * falloff((d - start) / (end - start)), with d measured as
 *    horizontal distance / (DH render distance in blocks). At d = start the value is exactly min,
 *    so vanilla and DH fog meet seamlessly at the vanilla edge.
 *    DH's configured falloff, end distance and max thickness are kept.
 */
public final class DistantHorizonsCompat {
    private DistantHorizonsCompat() {}

    private static boolean overridingVanillaFog = false;
    private static boolean disabled = false;

    public static void init() {
        try {
            DhApi.events.bind(DhApiBeforeFogRenderEvent.class, new FogHandler());
            ClientFogState.setChangeListener(DistantHorizonsCompat::syncVanillaFogOverride);
            FogControl.LOGGER.info("Distant Horizons detected: fog integration enabled");
        } catch (Throwable t) {
            disabled = true;
            FogControl.LOGGER.warn("Distant Horizons detected but its API is incompatible; fog integration disabled", t);
        }
    }

    /** Turns DH's vanilla-fog override on while a Fog Control server is active, off otherwise. */
    private static void syncVanillaFogOverride() {
        if (disabled) return;
        try {
            IDhApiConfig configs = DhApi.Delayed.configs;
            if (configs == null) return; // DH not initialised yet; retried from the fog event

            boolean want = ClientFogState.get() != null;
            if (want == overridingVanillaFog) return;

            IDhApiConfigValue<Boolean> vanillaFog = configs.graphics().fog().enableVanillaFog();
            if (want) {
                overridingVanillaFog = vanillaFog.setValue(true);
            } else {
                vanillaFog.clearValue();
                overridingVanillaFog = false;
            }
        } catch (Throwable t) {
            disable(t);
        }
    }

    private static void disable(Throwable t) {
        disabled = true;
        FogControl.LOGGER.warn("Distant Horizons fog integration failed and has been disabled", t);
    }

    private static final class FogHandler extends DhApiBeforeFogRenderEvent {
        @Override
        public void beforeRender(DhApiCancelableEventParam<EventParam> event) {
            if (disabled) return;
            try {
                syncVanillaFogOverride(); // in case settings arrived before DH was ready

                FogState state = ClientFogState.effective(); // includes any transition in progress
                float vanillaRenderDistance = ClientFogState.getAppliedRenderDistance();
                if (state == null || Float.isNaN(vanillaRenderDistance)) {
                    return; // our fog isn't active this frame (water, blindness, Nether...): leave DH alone
                }

                IDhApiConfig configs = DhApi.Delayed.configs;
                if (configs == null) return;
                int dhChunks = configs.graphics().chunkRenderDistance().getValue();
                if (dhChunks <= 0) return;
                float dhRenderDistance = dhChunks * 16.0F;

                DhApiFogRenderParam original = event.value.getOriginalFogRenderParam();
                DhApiMutableFogRenderParam fog = event.value.getFogRenderParam();

                float vanillaMax = clamp01(state.maxPercent() / 100.0F);
                float vanillaEdge = vanillaRenderDistance / dhRenderDistance; // in DH fog distance units

                float start = Math.max(original.getFarFogStartPercent(), vanillaEdge);
                float end = Math.max(original.getFarFogEndPercent(), start + 0.001F);

                fog.setFarFogStartPercent(start);
                fog.setFarFogEndPercent(end);
                fog.setFarFogMinThickness(vanillaMax);
                fog.setFarFogMaxThickness(Math.max(vanillaMax, original.getFarFogMaxThickness()));
            } catch (Throwable t) {
                disable(t);
            }
        }
    }

    private static float clamp01(float v) {
        return v < 0.0F ? 0.0F : Math.min(v, 1.0F);
    }
}
