package net.mokich.panopticon;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.mokich.panopticon.network.AdminEditPacket;
import net.mokich.panopticon.network.AdminOpenPacket;
import net.mokich.panopticon.network.AdminStatePacket;
import net.mokich.panopticon.network.BiomeTilePackets;
import net.mokich.panopticon.network.GiveRequestPacket;
import net.mokich.panopticon.network.OraclePackets;
import net.mokich.panopticon.network.PermsSyncPacket;
import net.mokich.panopticon.network.SeedPushPacket;
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
        registerPayloads();
        registerReceivers();
        registerEvents();
    }

    private void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(OraclePackets.Check.TYPE, OraclePackets.Check.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(OraclePackets.All.TYPE, OraclePackets.All.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(AdminOpenPacket.TYPE, AdminOpenPacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(AdminEditPacket.TYPE, AdminEditPacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(SpawnVillagerPacket.TYPE, SpawnVillagerPacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(GiveRequestPacket.TYPE, GiveRequestPacket.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(StructRegionPackets.Request.TYPE, StructRegionPackets.Request.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(BiomeTilePackets.Request.TYPE, BiomeTilePackets.Request.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(OraclePackets.CheckResult.TYPE, OraclePackets.CheckResult.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(OraclePackets.AllResult.TYPE, OraclePackets.AllResult.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(PermsSyncPacket.TYPE, PermsSyncPacket.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(AdminStatePacket.TYPE, AdminStatePacket.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(SeedPushPacket.TYPE, SeedPushPacket.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(StructRegionPackets.Result.TYPE, StructRegionPackets.Result.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(BiomeTilePackets.Result.TYPE, BiomeTilePackets.Result.STREAM_CODEC);
    }

    private void registerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(OraclePackets.Check.TYPE, OraclePackets.Check::handle);
        ServerPlayNetworking.registerGlobalReceiver(OraclePackets.All.TYPE, OraclePackets.All::handle);
        ServerPlayNetworking.registerGlobalReceiver(AdminOpenPacket.TYPE, AdminOpenPacket::handle);
        ServerPlayNetworking.registerGlobalReceiver(AdminEditPacket.TYPE, AdminEditPacket::handle);
        ServerPlayNetworking.registerGlobalReceiver(SpawnVillagerPacket.TYPE, SpawnVillagerPacket::handle);
        ServerPlayNetworking.registerGlobalReceiver(GiveRequestPacket.TYPE, GiveRequestPacket::handle);
        ServerPlayNetworking.registerGlobalReceiver(StructRegionPackets.Request.TYPE, StructRegionPackets.Request::handle);
        ServerPlayNetworking.registerGlobalReceiver(BiomeTilePackets.Request.TYPE, BiomeTilePackets.Request::handle);
    }

    private void registerEvents() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> PermsEvents.onLogin(handler.player));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PermsEvents.onLogout(handler.player));
        ServerTickEvents.END_SERVER_TICK.register(PermsEvents::onServerTick);
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> PermsCommand.register(dispatcher));
    }
}