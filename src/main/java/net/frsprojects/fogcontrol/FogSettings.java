package net.frsprojects.fogcontrol;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Immutable snapshot of the fog settings, shared between server and client.
 *
 * @param minPercent         fog amount right at the camera (0-100)
 * @param maxPercent         fog amount at the edge of the render distance (0-100)
 * @param customColorEnabled whether the fog uses {@code customColor} instead of vanilla's biome/sky color
 * @param customColor        fog color as 0xRRGGBB
 * @param skyColorEnabled    whether the sky uses {@code skyColor} instead of the biome's sky color
 * @param skyColor           sky color as 0xRRGGBB
 * @param transitionTicks    how long clients take to blend to new settings (ticks, 0 = instant)
 */
public record FogSettings(float minPercent, float maxPercent,
                          boolean customColorEnabled, int customColor,
                          boolean skyColorEnabled, int skyColor,
                          int transitionTicks) {

    public void write(FriendlyByteBuf buf) {
        buf.writeFloat(minPercent);
        buf.writeFloat(maxPercent);
        buf.writeBoolean(customColorEnabled);
        buf.writeInt(customColor);
        buf.writeBoolean(skyColorEnabled);
        buf.writeInt(skyColor);
        buf.writeVarInt(transitionTicks);
    }

    public static FogSettings read(FriendlyByteBuf buf) {
        return new FogSettings(buf.readFloat(), buf.readFloat(),
                buf.readBoolean(), buf.readInt(),
                buf.readBoolean(), buf.readInt(),
                buf.readVarInt());
    }
}
