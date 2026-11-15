package net.frsprojects.fogcontrol.command;

import net.frsprojects.fogcontrol.FogColor;
import net.frsprojects.fogcontrol.FogSettings;
import net.frsprojects.fogcontrol.config.FogConfig;
import net.frsprojects.fogcontrol.network.FogNetwork;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * /fogcontrol                          show current settings (anyone)
 * /fogcontrol min <0-100>              fog amount at the camera
 * /fogcontrol max <0-100>              fog amount at the render distance edge
 * /fogcontrol set <min> <max>          both at once
 * /fogcontrol reset                    restore min/max defaults
 *
 * /fogcontrol transition_speed <ticks> how long fog and sky take to blend to new settings (20 ticks = 1 s, 0 = instant)
 *
 * /fogcontrol color ...                fog color
 * /fogcontrol sky ...                  sky color
 *     (no argument)                    show (anyone)
 *     toggle | on | off                switch the custom color on/off
 *     rgb <r> <g> <b>                  set color (0-255 each) and switch it on
 *     hex <#RRGGBB>                    set color and switch it on
 */
public final class FogCommand {
    private FogCommand() {}

    private static final int OP_LEVEL = 2;

    /** The two colors that share the same set of subcommands. */
    private enum ColorTarget {
        FOG("Fog color"),
        SKY("Sky color");

        final String label;

        ColorTarget(String label) {
            this.label = label;
        }

        ForgeConfigSpec.BooleanValue enabled() {
            return this == FOG ? FogConfig.CUSTOM_COLOR_ENABLED : FogConfig.SKY_COLOR_ENABLED;
        }

        ForgeConfigSpec.ConfigValue<String> color() {
            return this == FOG ? FogConfig.COLOR : FogConfig.SKY_COLOR;
        }

        boolean enabledIn(FogSettings s) {
            return this == FOG ? s.customColorEnabled() : s.skyColorEnabled();
        }

        int colorIn(FogSettings s) {
            return this == FOG ? s.customColor() : s.skyColor();
        }
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("fogcontrol")
                .executes(FogCommand::show)

                .then(Commands.literal("min").requires(s -> s.hasPermission(OP_LEVEL))
                        .then(Commands.argument("percent", DoubleArgumentType.doubleArg(0.0, 100.0))
                                .executes(ctx -> updateAmounts(ctx,
                                        DoubleArgumentType.getDouble(ctx, "percent"),
                                        FogConfig.MAX_PERCENT.get()))))

                .then(Commands.literal("max").requires(s -> s.hasPermission(OP_LEVEL))
                        .then(Commands.argument("percent", DoubleArgumentType.doubleArg(0.0, 100.0))
                                .executes(ctx -> updateAmounts(ctx,
                                        FogConfig.MIN_PERCENT.get(),
                                        DoubleArgumentType.getDouble(ctx, "percent")))))

                .then(Commands.literal("set").requires(s -> s.hasPermission(OP_LEVEL))
                        .then(Commands.argument("min", DoubleArgumentType.doubleArg(0.0, 100.0))
                                .then(Commands.argument("max", DoubleArgumentType.doubleArg(0.0, 100.0))
                                        .executes(ctx -> updateAmounts(ctx,
                                                DoubleArgumentType.getDouble(ctx, "min"),
                                                DoubleArgumentType.getDouble(ctx, "max"))))))

                .then(Commands.literal("reset").requires(s -> s.hasPermission(OP_LEVEL))
                        .executes(ctx -> updateAmounts(ctx, FogConfig.DEFAULT_MIN, FogConfig.DEFAULT_MAX)))

                .then(Commands.literal("transition_speed")
                        .executes(FogCommand::showTransition)
                        .then(Commands.argument("ticks", IntegerArgumentType.integer(0, FogConfig.MAX_TRANSITION_TICKS))
                                .requires(s -> s.hasPermission(OP_LEVEL))
                                .executes(ctx -> {
                                    FogConfig.TRANSITION_TICKS.set(IntegerArgumentType.getInteger(ctx, "ticks"));
                                    return saveAndSync(ctx, describeTransition(FogConfig.snapshot()));
                                })))

                .then(colorBranch("color", ColorTarget.FOG))
                .then(colorBranch("sky", ColorTarget.SKY)));
    }

    /** show / toggle / on / off / rgb / hex for one color. */
    private static LiteralArgumentBuilder<CommandSourceStack> colorBranch(String name, ColorTarget target) {
        return Commands.literal(name)
                .executes(ctx -> showColor(ctx, target))

                .then(Commands.literal("toggle").requires(s -> s.hasPermission(OP_LEVEL))
                        .executes(ctx -> setColorEnabled(ctx, target, !target.enabled().get())))

                .then(Commands.literal("on").requires(s -> s.hasPermission(OP_LEVEL))
                        .executes(ctx -> setColorEnabled(ctx, target, true)))

                .then(Commands.literal("off").requires(s -> s.hasPermission(OP_LEVEL))
                        .executes(ctx -> setColorEnabled(ctx, target, false)))

                .then(Commands.literal("rgb").requires(s -> s.hasPermission(OP_LEVEL))
                        .then(Commands.argument("red", IntegerArgumentType.integer(0, 255))
                                .then(Commands.argument("green", IntegerArgumentType.integer(0, 255))
                                        .then(Commands.argument("blue", IntegerArgumentType.integer(0, 255))
                                                .executes(ctx -> setColor(ctx, target, FogColor.fromRgb(
                                                        IntegerArgumentType.getInteger(ctx, "red"),
                                                        IntegerArgumentType.getInteger(ctx, "green"),
                                                        IntegerArgumentType.getInteger(ctx, "blue"))))))))

                .then(Commands.literal("hex").requires(s -> s.hasPermission(OP_LEVEL))
                        // greedyString so "#" can be typed without quotes
                        .then(Commands.argument("color", StringArgumentType.greedyString())
                                .executes(ctx -> {
                                    String text = StringArgumentType.getString(ctx, "color");
                                    int rgb = FogColor.parseHex(text);
                                    if (rgb < 0) {
                                        ctx.getSource().sendFailure(Component.literal(
                                                "Invalid color '" + text + "'. Use #RRGGBB, e.g. #88AACC."));
                                        return 0;
                                    }
                                    return setColor(ctx, target, rgb);
                                })));
    }

    // ---- fog amount ----

    private static int updateAmounts(CommandContext<CommandSourceStack> ctx, double min, double max) {
        if (min > max) {
            ctx.getSource().sendFailure(Component.literal(String.format(
                    "Minimum fog (%.1f%%) can't be higher than maximum fog (%.1f%%).", min, max)));
            return 0;
        }
        FogConfig.MIN_PERCENT.set(min);
        FogConfig.MAX_PERCENT.set(max);
        return saveAndSync(ctx, Component.literal("Fog updated: ").append(describe(FogConfig.snapshot())));
    }

    // ---- colors ----

    private static int setColorEnabled(CommandContext<CommandSourceStack> ctx, ColorTarget target, boolean enabled) {
        target.enabled().set(enabled);
        return saveAndSync(ctx, describeColor(target, FogConfig.snapshot()));
    }

    private static int setColor(CommandContext<CommandSourceStack> ctx, ColorTarget target, int rgb) {
        target.color().set(FogColor.toHex(rgb));
        target.enabled().set(true); // setting a color implies you want to see it
        return saveAndSync(ctx, describeColor(target, FogConfig.snapshot()));
    }

    // ---- shared ----

    /** Save to the world's server config and push to every connected player. */
    private static int saveAndSync(CommandContext<CommandSourceStack> ctx, Component message) {
        FogConfig.save();
        FogNetwork.broadcastCurrentSettings();
        ctx.getSource().sendSuccess(() -> message, true);
        return 1;
    }

    private static int show(CommandContext<CommandSourceStack> ctx) {
        FogSettings s = FogConfig.snapshot();
        ctx.getSource().sendSuccess(() -> Component.literal("Fog Control: ").append(describe(s)), false);
        ctx.getSource().sendSuccess(() -> describeColor(ColorTarget.FOG, s), false);
        ctx.getSource().sendSuccess(() -> describeColor(ColorTarget.SKY, s), false);
        ctx.getSource().sendSuccess(() -> describeTransition(s), false);
        return 1;
    }

    private static int showTransition(CommandContext<CommandSourceStack> ctx) {
        FogSettings s = FogConfig.snapshot();
        ctx.getSource().sendSuccess(() -> describeTransition(s), false);
        return 1;
    }

    private static Component describeTransition(FogSettings s) {
        int ticks = s.transitionTicks();
        if (ticks == 0) return Component.literal("Transition: instant");
        return Component.literal(String.format("Transition: %d ticks (%.1f seconds)", ticks, ticks / 20.0));
    }

    private static int showColor(CommandContext<CommandSourceStack> ctx, ColorTarget target) {
        FogSettings s = FogConfig.snapshot();
        ctx.getSource().sendSuccess(() -> describeColor(target, s), false);
        return 1;
    }

    private static Component describe(FogSettings s) {
        return Component.literal(String.format(
                "%.1f%% at the camera, %.1f%% at the render distance edge", s.minPercent(), s.maxPercent()));
    }

    private static Component describeColor(ColorTarget target, FogSettings s) {
        int rgb = target.colorIn(s);
        boolean on = target.enabledIn(s);
        MutableComponent swatch = Component.literal("\u25A0")
                .withStyle(style -> style.withColor(TextColor.fromRgb(rgb)));
        String info = String.format(" %s (%d, %d, %d)", FogColor.toHex(rgb), (rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
        return Component.literal(target.label + ": " + (on ? "custom " : "vanilla (custom off, saved: "))
                .append(swatch)
                .append(Component.literal(info + (on ? "" : ")")));
    }
}
