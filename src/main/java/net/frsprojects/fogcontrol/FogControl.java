package net.frsprojects.fogcontrol;

import net.frsprojects.fogcontrol.compat.DistantHorizonsCompat;
import net.frsprojects.fogcontrol.config.FogConfig;
import net.frsprojects.fogcontrol.network.FogNetwork;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

@Mod(FogControl.MOD_ID)
public class FogControl {
    public static final String MOD_ID = "fogcontrol";
    public static final Logger LOGGER = LogUtils.getLogger();

    public FogControl() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        // SERVER config: stored per world in <world>/serverconfig/fogcontrol-server.toml
        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER, FogConfig.SPEC);
        modBus.addListener(this::onConfigReloaded);
        modBus.addListener(this::onClientSetup);

        FogNetwork.register();
    }

    /** Optional Distant Horizons integration; the compat class is only loaded if DH is present. */
    private void onClientSetup(FMLClientSetupEvent event) {
        if (ModList.get().isLoaded("distanthorizons")) {
            event.enqueueWork(DistantHorizonsCompat::init);
        }
    }

    /** If the config file is edited by hand while the server runs, push the new values to everyone. */
    private void onConfigReloaded(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != FogConfig.SPEC) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            // This event fires on the file-watcher thread; hop onto the server thread.
            server.execute(FogNetwork::broadcastCurrentSettings);
        }
    }
}
