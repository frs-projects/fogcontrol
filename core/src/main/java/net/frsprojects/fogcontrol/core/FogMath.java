package net.frsprojects.fogcontrol.core;

/**
 * Converts min/max fog amounts into the fog start/end distances the shaders use.
 *
 * Both vanilla (fog.glsl, linear_fog) and Embeddium (sodium:include/fog.glsl, _linearFog) compute
 *     amount = smoothstep(fogStart, fogEnd, distance)
 * We want amount = min at distance 0 and amount = max at the render distance R.
 * With t0 = inverseSmoothstep(min) and t1 = inverseSmoothstep(max), that means
 *     (0 - start) / (end - start) = t0
 *     (R - start) / (end - start) = t1
 * so  end - start = R / (t1 - t0),  start = -t0 * (end - start).
 * A negative start is fine: the shaders only skip fog when distance <= fogStart.
 */
public final class FogMath {
    private FogMath() {}

    /**
     * "No fog" is expressed as start = 0 and an enormous end, so the fog amount is ~0 everywhere.
     * Keeping start <= 0 matters: patched shader packs detect Fog Control by fogStart <= 0,
     * which vanilla terrain fog never produces.
     */
    private static final float NO_FOG_END_MULTIPLIER = 1.0E6F;

    /** Smallest allowed gap between t0 and t1; min == max approximates uniform fog. */
    private static final double MIN_T_GAP = 1.0E-3;

    public record FogRange(float start, float end) {}

    public static FogRange compute(float renderDistance, float minPercent, float maxPercent) {
        double max = clamp01(maxPercent / 100.0);
        double min = Math.min(clamp01(minPercent / 100.0), max); // fog can't decrease with distance

        if (max <= 0.0) {
            return new FogRange(0.0F, renderDistance * NO_FOG_END_MULTIPLIER);
        }

        double t0 = inverseSmoothstep(min);
        double t1 = inverseSmoothstep(max);
        double length = renderDistance / Math.max(t1 - t0, MIN_T_GAP);
        double start = -t0 * length;
        return new FogRange((float) start, (float) (start + length));
    }

    /** Solves 3t^2 - 2t^3 = y for t in [0, 1]. */
    public static double inverseSmoothstep(double y) {
        double clamped = clamp01(y);
        return 0.5 - Math.sin(Math.asin(1.0 - 2.0 * clamped) / 3.0);
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : Math.min(v, 1.0);
    }
}
