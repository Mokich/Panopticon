package net.mokich.panopticon.perms;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;
import net.mokich.panopticon.Panopticon;
import net.mokich.panopticon.network.PermsSyncPacket;
import net.mokich.panopticon.network.SeedPushPacket;

import java.util.*;
import net.minecraft.server.permissions.Permissions;

@EventBusSubscriber(modid = Panopticon.MODID)
public final class PermsEvents {
    public static final List<PermissionNode<?>> PERM_NODES = new ArrayList<>();
    public static PermissionNode<Boolean> adminNode;

    private PermsEvents() {
    }

    @SubscribeEvent
    public static void onGatherNodes(PermissionGatherEvent.Nodes event) {
        if (!Panopticon.ACTIVE) {
            return;
        }
        PERM_NODES.clear();
        adminNode = new PermissionNode<>("panoptic", "admin", PermissionTypes.BOOLEAN,
                (player, uuid, context) -> player != null && player.permissions().hasPermission(Permissions.COMMANDS_ADMIN));
        PERM_NODES.add(adminNode);
        event.addNodes(PERM_NODES);
    }

    public static List<String> effectiveNodes(ServerPlayer p) {
        List<String> nodes = new ArrayList<>(PermsStore.nodesFor(p));
        if (PermsAdmin.isAdmin(p)) {
            nodes.add("panoptic.admin");
        }
        return nodes;
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (Panopticon.ACTIVE && event.getEntity() instanceof ServerPlayer sp) {
            sync(sp);
        }
    }

    public static void sync(ServerPlayer p) {
        if (!Panopticon.ACTIVE) {
            return;
        }
        List<String> nodes = effectiveNodes(p);
        lastSynced.put(p.getUUID(), nodes);
        PacketDistributor.sendToPlayer(p, new PermsSyncPacket(nodes));
        boolean seedGranted = nodes.contains("panoptic.seed.view");
        long seed = p.level().getServer() != null && p.level().getServer().overworld() != null
                ? p.level().getServer().overworld().getSeed() : 0L;
        PacketDistributor.sendToPlayer(p, new SeedPushPacket(seedGranted, seedGranted ? seed : 0L));
    }

    public static void resyncAll(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            sync(p);
            server.getCommands().sendCommands(p);
        }
    }

    private static final Map<UUID, List<String>> lastSynced = new HashMap<>();
    private static int tick;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!Panopticon.ACTIVE || ++tick % 40 != 0) {
            return;
        }
        for (ServerPlayer p : event.getServer().getPlayerList().getPlayers()) {
            List<String> now = effectiveNodes(p);
            if (!now.equals(lastSynced.get(p.getUUID()))) {
                sync(p);
                event.getServer().getCommands().sendCommands(p);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        lastSynced.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        if (Panopticon.ACTIVE) {
            PermsCommand.register(event.getDispatcher());
        }
    }
}