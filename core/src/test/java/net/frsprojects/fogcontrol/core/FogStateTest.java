package net.frsprojects.fogcontrol.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FogStateTest {
    private static final float EPS = 1e-6F;

    @Test
    void ofSetsWeightsFromEnabledFlags() {
        FogState s = FogState.of(10, 70, true, 0xFF0000, false, 0x0000FF);
        assertEquals(1.0F, s.fogColorWeight());
        assertEquals(1.0F, s.fogR());
        assertEquals(0.0F, s.skyColorWeight());
        assertEquals(1.0F, s.skyB());
    }

    @Test
    void lerpBlendsEveryValue() {
        FogState a = FogState.of(0, 40, false, 0x000000, false, 0x000000);
        FogState b = FogState.of(20, 100, true, 0xFFFFFF, true, 0xFFFFFF);
        FogState mid = FogState.lerp(a, b, 0.5F);
        assertEquals(10.0F, mid.minPercent(), EPS);
        assertEquals(70.0F, mid.maxPercent(), EPS);
        assertEquals(0.5F, mid.fogColorWeight(), EPS);
        assertEquals(0.5F, mid.fogG(), EPS);
        assertEquals(0.5F, mid.skyColorWeight(), EPS);
        assertEquals(b, FogState.lerp(a, b, 1.0F));
    }

    @Test
    void hiddenColorStartsAtTargetRgb() {
        // Turning a custom color on fades from vanilla straight to the new color,
        // not through the old saved one.
        FogState current = FogState.of(0, 70, false, 0xFF0000, true, 0x00FF00);
        FogState target = FogState.of(0, 70, true, 0x0000FF, true, 0x0000FF);
        FogState start = current.startingPointFor(target);
        assertEquals(0.0F, start.fogColorWeight());
        assertEquals(0.0F, start.fogR());
        assertEquals(1.0F, start.fogB());
        // A visible color keeps its current RGB and blends from there.
        assertEquals(1.0F, start.skyG());
        assertEquals(0.0F, start.skyB());
    }
}
