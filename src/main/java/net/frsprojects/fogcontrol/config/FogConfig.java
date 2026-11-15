package net.frsprojects.fogcontrol.config;

import net.frsprojects.fogcontrol.FogColor;
import net.frsprojects.fogcontrol.FogSettings;
import net.minecraftforge.common.ForgeConfigSpec;

public final class FogConfig {
    private FogConfig() {}

    public static final double DEFAULT_MIN = 0.0;
    public static final double DEFAULT_MAX = 70.0;
    public static final boolean DEFAULT_CUSTOM_COLOR_ENABLED = false;
    public static final String DEFAULT_COLOR = "#C0D0E0";
    public static final boolean DEFAULT_SKY_COLOR_ENABLED = false;
    public static final String DEFAULT_SKY_COLOR = "#78A7FF"; // vanilla plains sky
    public static final int DEFAULT_TRANSITION_TICKS = 200; // 10 seconds
    public static final int MAX_TRANSITION_TICKS = 72000;   // 1 hour

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.DoubleValue MIN_PERCENT;
    public static final ForgeConfigSpec.DoubleValue MAX_PERCENT;
    public static final ForgeConfigSpec.BooleanValue CUSTOM_COLOR_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<String> COLOR;
    public static final ForgeConfigSpec.BooleanValue SKY_COLOR_ENABLED;
    public static final ForgeConfigSpec.ConfigValue<String> SKY_COLOR;
    public static final ForgeConfigSpec.IntValue TRANSITION_TICKS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Fog Control settings. Synced to every connected player.",
                  "Change in-game with /fogcontrol (op level 2).",
                  "Fog goes from minFogPercent at the camera to maxFogPercent at the render distance edge.").push("fog");

        MIN_PERCENT = b.comment("Fog amount right at the camera. 0 = clear up close.",
                                "If set above maxFogPercent, it is treated as equal to it.")
                .defineInRange("minFogPercent", DEFAULT_MIN, 0.0, 100.0);

        MAX_PERCENT = b.comment("Fog amount at the edge of the render distance. 100 = fully opaque, 0 = no fog at all.")
                .defineInRange("maxFogPercent", DEFAULT_MAX, 0.0, 100.0);

        CUSTOM_COLOR_ENABLED = b.comment("Use customColor instead of vanilla's biome/sky-based fog color.")
                .define("customColorEnabled", DEFAULT_CUSTOM_COLOR_ENABLED);

        COLOR = b.comment("Custom fog color as #RRGGBB (daytime color; it darkens at night but not in rain).")
                .define("customColor", DEFAULT_COLOR, o -> o instanceof String s && FogColor.parseHex(s) >= 0);

        SKY_COLOR_ENABLED = b.comment("Use skyColor instead of the biome's sky color.")
                .define("skyColorEnabled", DEFAULT_SKY_COLOR_ENABLED);

        SKY_COLOR = b.comment("Custom sky color as #RRGGBB (daytime color; it darkens at night but not in rain).")
                .define("skyColor", DEFAULT_SKY_COLOR, o -> o instanceof String s && FogColor.parseHex(s) >= 0);

        TRANSITION_TICKS = b.comment("How long fog and sky take to blend to new settings, in ticks (20 ticks = 1 second). 0 = instant.")
                .defineInRange("transitionTicks", DEFAULT_TRANSITION_TICKS, 0, MAX_TRANSITION_TICKS);

        b.pop();
        SPEC = b.build();
    }

    public static FogSettings snapshot() {
        return new FogSettings(
                MIN_PERCENT.get().floatValue(),
                MAX_PERCENT.get().floatValue(),
                CUSTOM_COLOR_ENABLED.get(),
                parseOr(COLOR.get(), DEFAULT_COLOR),
                SKY_COLOR_ENABLED.get(),
                parseOr(SKY_COLOR.get(), DEFAULT_SKY_COLOR),
                TRANSITION_TICKS.get());
    }

    private static int parseOr(String value, String fallback) {
        int rgb = FogColor.parseHex(value);
        return rgb >= 0 ? rgb : FogColor.parseHex(fallback);
    }

    public static void save() {
        SPEC.save();
    }
}
