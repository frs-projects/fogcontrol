package net.frsprojects.fogcontrol.network;

import net.frsprojects.fogcontrol.FogSettings;
import net.frsprojects.fogcontrol.client.ClientFogState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: the current fog settings. */
public record SyncFogSettingsPacket(FogSettings settings) {

    public static void encode(SyncFogSettingsPacket msg, FriendlyByteBuf buf) {
        msg.settings.write(buf);
    }

    public static SyncFogSettingsPacket decode(FriendlyByteBuf buf) {
        return new SyncFogSettingsPacket(FogSettings.read(buf));
    }

    /** Runs on the client main thread (registered via consumerMainThread). */
    public static void handle(SyncFogSettingsPacket msg, Supplier<NetworkEvent.Context> ctx) {
        // ClientFogState only holds plain data (no client-only imports), so this is safe to reference.
        ClientFogState.set(msg.settings);
    }
}
