package net.frsprojects.fogcontrol.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FogMathTest {
    private static final double EPS = 1e-3;
    /** min == max is approximated with a tiny slope (FogMath.MIN_T_GAP), off by ~0.15% at the edge. */
    private static final double UNIFORM_EPS = 5e-3;
    private static final float R = 192.0F;

    /** What the vanilla / Embeddium shaders compute: smoothstep(start, end, distance). */
    private static double shaderFog(FogMath.FogRange range, double distance) {
        if (distance <= range.start()) return 0.0;
        double t = Math.min(Math.max((distance - range.start()) / (range.end() - range.start()), 0.0), 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    @Test
    void inverseSmoothstepUndoesSmoothstep() {
        for (int i = 0; i <= 100; i++) {
            double y = i / 100.0;
            double t = FogMath.inverseSmoothstep(y);
            assertTrue(t >= 0.0 && t <= 1.0);
            assertEquals(y, t * t * (3.0 - 2.0 * t), 1e-9);
        }
    }

    @Test
    void minAtCameraMaxAtRenderDistance() {
        float[][] cases = {{0, 70}, {0, 100}, {50, 100}, {10, 40}, {25, 26}};
        for (float[] c : cases) {
            FogMath.FogRange range = FogMath.compute(R, c[0], c[1]);
            assertEquals(c[0] / 100.0, shaderFog(range, 0.0), EPS, "min for " + c[0] + "/" + c[1]);
            assertEquals(c[1] / 100.0, shaderFog(range, R), EPS, "max for " + c[0] + "/" + c[1]);
        }
    }

    @Test
    void equalMinAndMaxIsUniformHaze() {
        FogMath.FogRange range = FogMath.compute(R, 30, 30);
        assertEquals(0.3, shaderFog(range, 0.0), UNIFORM_EPS);
        assertEquals(0.3, shaderFog(range, R / 2), UNIFORM_EPS);
        assertEquals(0.3, shaderFog(range, R), UNIFORM_EPS);
    }

    @Test
    void fogStartIsNeverPositive() {
        // Patched shader packs detect Fog Control by fogStart <= 0.
        for (int min = 0; min <= 100; min += 10) {
            for (int max = min; max <= 100; max += 10) {
                assertTrue(FogMath.compute(R, min, max).start() <= 0.0F, min + "/" + max);
            }
        }
    }

    @Test
    void zeroMaxMeansNoFog() {
        FogMath.FogRange range = FogMath.compute(R, 0, 0);
        assertEquals(0.0F, range.start());
        assertTrue(shaderFog(range, R) < 1e-9);
    }

    @Test
    void minAboveMaxIsTreatedAsMax() {
        FogMath.FogRange range = FogMath.compute(R, 80, 40);
        assertEquals(0.4, shaderFog(range, 0.0), UNIFORM_EPS);
        assertEquals(0.4, shaderFog(range, R), UNIFORM_EPS);
    }
}
