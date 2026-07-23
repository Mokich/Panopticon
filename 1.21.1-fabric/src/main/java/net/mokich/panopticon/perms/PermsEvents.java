package net.mokich.panopticon.perms;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.mokich.panopticon.Panopticon;
import net.mokich.panopticon.network.PermsSyncPacket;
import net.mokich.panopticon.network.SeedPushPacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class PermsEvents {
    private static final Map<UUID, List<String>> lastSynced = new HashMap<>();
    private static int tick;

    private PermsEvents() {
    }

    public static List<String> effectiveNodes(ServerPlayer p) {
        List<String> nodes = new ArrayList<>(PermsStore.nodesFor(p));
        if (PermsAdmin.isAdmin(p)) {
            nodes.add("panoptic.admin");
        }
        return nodes;
    }

    public static void onLogin(ServerPlayer p) {
        if (Panopticon.ACTIVE && p != null) {
            sync(p);
        }
    }

    public static void sync(ServerPlayer p) {
        if (!Panopticon.ACTIVE) {
            return;
        }
        List<String> nodes = effectiveNodes(p);
        lastSynced.put(p.getUUID(), nodes);
        ServerPlayNetworking.send(p, new PermsSyncPacket(nodes));
        boolean seedGranted = nodes.contains("panoptic.seed.view");
        long seed = p.getServer() != null && p.getServer().overworld() != null
                ? p.getServer().overworld().getSeed() : 0L;
        ServerPlayNetworking.send(p, new SeedPushPacket(seedGranted, seedGranted ? seed : 0L));
    }

    public static void resyncAll(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            sync(p);
            server.getCommands().sendCommands(p);
        }
    }

    public static void onServerTick(MinecraftServer server) {
        if (!Panopticon.ACTIVE || ++tick % 40 != 0) {
            return;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            List<String> now = effectiveNodes(p);
            if (!now.equals(lastSynced.get(p.getUUID()))) {
                sync(p);
                server.getCommands().sendCommands(p);
            }
        }
    }

    public static void onLogout(ServerPlayer p) {
        if (p != null) {
            lastSynced.remove(p.getUUID());
        }
    }
}