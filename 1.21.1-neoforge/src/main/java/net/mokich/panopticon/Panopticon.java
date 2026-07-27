package net.mokich.panopticon;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.mokich.panopticon.network.*;

@Mod(Panopticon.MODID)
public class Panopticon {
    public static final String MODID = "panopticon";
    public static volatile boolean ACTIVE = false;

    public Panopticon(IEventBus modEventBus, ModContainer modContainer) {
        if (!FMLEnvironment.dist.isDedicatedServer()) {
            return;
        }
        if (ModList.get().isLoaded("panoptic")) {
            throw new IllegalStateException(
                    "Panopticon replaces Panoptic on the server. Remove Panoptic from the server mods folder.");
        }
        modEventBus.addListener(this::registerPayloads);
        ACTIVE = true;
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1").optional();

        r.playToServer(OraclePackets.Check.TYPE, OraclePackets.Check.STREAM_CODEC, OraclePackets.Check::handle);
        r.playToServer(OraclePackets.All.TYPE, OraclePackets.All.STREAM_CODEC, OraclePackets.All::handle);
        r.playToServer(AdminOpenPacket.TYPE, AdminOpenPacket.STREAM_CODEC, AdminOpenPacket::handle);
        r.playToServer(AdminEditPacket.TYPE, AdminEditPacket.STREAM_CODEC, AdminEditPacket::handle);
        r.playToServer(SpawnVillagerPacket.TYPE, SpawnVillagerPacket.STREAM_CODEC, SpawnVillagerPacket::handle);
        r.playToServer(GiveRequestPacket.TYPE, GiveRequestPacket.STREAM_CODEC, GiveRequestPacket::handle);
        r.playToServer(StructRegionPackets.Request.TYPE, StructRegionPackets.Request.STREAM_CODEC, StructRegionPackets.Request::handle);
        r.playToServer(BiomeTilePackets.Request.TYPE, BiomeTilePackets.Request.STREAM_CODEC, BiomeTilePackets.Request::handle);
        r.playToServer(TeleportSurfacePacket.TYPE, TeleportSurfacePacket.STREAM_CODEC, TeleportSurfacePacket::handle);

        r.playToClient(OraclePackets.CheckResult.TYPE, OraclePackets.CheckResult.STREAM_CODEC, OraclePackets.CheckResult::handle);
        r.playToClient(OraclePackets.AllResult.TYPE, OraclePackets.AllResult.STREAM_CODEC, OraclePackets.AllResult::handle);
        r.playToClient(PermsSyncPacket.TYPE, PermsSyncPacket.STREAM_CODEC, PermsSyncPacket::handle);
        r.playToClient(AdminStatePacket.TYPE, AdminStatePacket.STREAM_CODEC, AdminStatePacket::handle);
        r.playToClient(SeedPushPacket.TYPE, SeedPushPacket.STREAM_CODEC, SeedPushPacket::handle);
        r.playToClient(StructRegionPackets.Result.TYPE, StructRegionPackets.Result.STREAM_CODEC, StructRegionPackets.Result::handle);
        r.playToClient(BiomeTilePackets.Result.TYPE, BiomeTilePackets.Result.STREAM_CODEC, BiomeTilePackets.Result::handle);
    }
}