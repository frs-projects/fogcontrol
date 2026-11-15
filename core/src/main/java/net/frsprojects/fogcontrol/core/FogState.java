package net.frsprojects.fogcontrol.core;

/**
 * The fog/sky values actually shown on screen. During a transition this is a blend between
 * the previous and the new settings. Plain data, no Minecraft imports.
 *
 * Colors are stored as a weight (0 = vanilla color, 1 = custom color) plus the custom RGB, so
 * switching a custom color on or off fades from/to the live vanilla color instead of popping.
 */
public record FogState(float minPercent, float maxPercent,
                       float fogColorWeight, float fogR, float fogG, float fogB,
                       float skyColorWeight, float skyR, float skyG, float skyB) {

    /** The state that fully shows the given settings (custom colors at weight 1 when enabled). */
    public static FogState of(float minPercent, float maxPercent,
                              boolean fogColorEnabled, int fogColor,
                              boolean skyColorEnabled, int skyColor) {
        return new FogState(minPercent, maxPercent,
                fogColorEnabled ? 1.0F : 0.0F, FogColor.red(fogColor), FogColor.green(fogColor), FogColor.blue(fogColor),
                skyColorEnabled ? 1.0F : 0.0F, FogColor.red(skyColor), FogColor.green(skyColor), FogColor.blue(skyColor));
    }

    /**
     * Prepares this (current) state as the starting point of a transition to {@code target}.
     * If a custom color is currently invisible (weight 0), its RGB doesn't matter yet, so take the
     * target's RGB right away. Otherwise turning a color on would sweep through the old saved color.
     */
    public FogState startingPointFor(FogState target) {
        boolean fogHidden = fogColorWeight <= 0.0F;
        boolean skyHidden = skyColorWeight <= 0.0F;
        return new FogState(minPercent, maxPercent,
                fogColorWeight,
                fogHidden ? target.fogR : fogR, fogHidden ? target.fogG : fogG, fogHidden ? target.fogB : fogB,
                skyColorWeight,
                skyHidden ? target.skyR : skyR, skyHidden ? target.skyG : skyG, skyHidden ? target.skyB : skyB);
    }

    public static FogState lerp(FogState a, FogState b, float t) {
        return new FogState(
                mix(a.minPercent, b.minPercent, t), mix(a.maxPercent, b.maxPercent, t),
                mix(a.fogColorWeight, b.fogColorWeight, t),
                mix(a.fogR, b.fogR, t), mix(a.fogG, b.fogG, t), mix(a.fogB, b.fogB, t),
                mix(a.skyColorWeight, b.skyColorWeight, t),
                mix(a.skyR, b.skyR, t), mix(a.skyG, b.skyG, t), mix(a.skyB, b.skyB, t));
    }

    private static float mix(float a, float b, float t) {
        return a + (b - a) * t;
    }
}
