package net.mokich.panopticon;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.mokich.panopticon.network.AdminEditPacket;
import net.mokich.panopticon.network.AdminOpenPacket;
import net.mokich.panopticon.network.BiomeTilePackets;
import net.mokich.panopticon.network.GiveRequestPacket;
import net.mokich.panopticon.network.OraclePackets;
import net.mokich.panopticon.network.SpawnVillagerPacket;
import net.mokich.panopticon.network.StructRegionPackets;
import net.mokich.panopticon.perms.PermsCommand;
import net.mokich.panopticon.perms.PermsEvents;

public final class PanopticonMod implements ModInitializer {
    @Override
    public void onInitialize() {
        if (FabricLoader.getInstance().isModLoaded("panoptic")) {
            throw new IllegalStateException(
                    "Panopticon replaces Panoptic on the server. Remove Panoptic from the server mods folder.");
        }
        Panopticon.ACTIVE = true;
        registerReceivers();
        registerEvents();
    }

    private void registerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(OraclePackets.Check.CHANNEL,
                (server, player, handler, buf, responseSender) -> {
                    OraclePackets.Check msg = OraclePackets.Check.decode(buf);
                    server.execute(() -> OraclePackets.Check.handle(msg, player));
                });
        ServerPlayNetworking.registerGlobalReceiver(OraclePackets.All.CHANNEL,
                (server, player, handler, buf, responseSender) -> {
                    OraclePackets.All msg = OraclePackets.All.decode(buf);
                    server.execute(() -> OraclePackets.All.handle(msg, player));
                });
        ServerPlayNetworking.registerGlobalReceiver(AdminOpenPacket.CHANNEL,
                (server, player, handler, buf, responseSender) -> {
                    AdminOpenPacket msg = AdminOpenPacket.decode(buf);
                    server.execute(() -> AdminOpenPacket.handle(msg, player));
                });
        ServerPlayNetworking.registerGlobalReceiver(AdminEditPacket.CHANNEL,
                (server, player, handler, buf, responseSender) -> {
                    AdminEditPacket msg = AdminEditPacket.decode(buf);
                    server.execute(() -> AdminEditPacket.handle(msg, player));
                });
        ServerPlayNetworking.registerGlobalReceiver(SpawnVillagerPacket.CHANNEL,
                (server, player, handler, buf, responseSender) -> {
                    SpawnVillagerPacket msg = SpawnVillagerPacket.decode(buf);
                    server.execute(() -> SpawnVillagerPacket.handle(msg, player));
                });
        ServerPlayNetworking.registerGlobalReceiver(GiveRequestPacket.CHANNEL,
                (server, player, handler, buf, responseSender) -> {
                    GiveRequestPacket msg = GiveRequestPacket.decode(buf);
                    server.execute(() -> GiveRequestPacket.handle(msg, player));
                });
        ServerPlayNetworking.registerGlobalReceiver(StructRegionPackets.Request.CHANNEL,
                (server, player, handler, buf, responseSender) -> {
                    StructRegionPackets.Request msg = StructRegionPackets.Request.decode(buf);
                    server.execute(() -> StructRegionPackets.Request.handle(msg, player));
                });
        ServerPlayNetworking.registerGlobalReceiver(BiomeTilePackets.Request.CHANNEL,
                (server, player, handler, buf, responseSender) -> {
                    BiomeTilePackets.Request msg = BiomeTilePackets.Request.decode(buf);
                    server.execute(() -> BiomeTilePackets.Request.handle(msg, player));
                });
    }

    private void registerEvents() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> PermsEvents.onLogin(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PermsEvents.onLogout(handler.player));
        ServerTickEvents.END_SERVER_TICK.register(PermsEvents::onServerTick);
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> PermsCommand.register(dispatcher));
    }
}