package net.frsprojects.fogcontrol.network;

import net.frsprojects.fogcontrol.FogControl;
import net.frsprojects.fogcontrol.config.FogConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class FogNetwork {
    private FogNetwork() {}

    private static final String PROTOCOL = "5";

    // acceptMissingOr: players without the mod can still join (they just see vanilla fog),
    // and a client with the mod can join servers that don't have it.
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(FogControl.MOD_ID, "main"),
            () -> PROTOCOL,
            NetworkRegistry.acceptMissingOr(PROTOCOL),
            NetworkRegistry.acceptMissingOr(PROTOCOL));

    public static void register() {
        CHANNEL.messageBuilder(SyncFogSettingsPacket.class, 0, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncFogSettingsPacket::encode)
                .decoder(SyncFogSettingsPacket::decode)
                .consumerMainThread(SyncFogSettingsPacket::handle)
                .add();
    }

    public static void sendCurrentSettings(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SyncFogSettingsPacket(FogConfig.snapshot()));
    }

    public static void broadcastCurrentSettings() {
        CHANNEL.send(PacketDistributor.ALL.noArg(), new SyncFogSettingsPacket(FogConfig.snapshot()));
    }
}
