package net.mokich.panopticon.perms;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;
import net.mokich.panopticon.Panopticon;
import net.mokich.panopticon.network.PermsSyncPacket;
import net.mokich.panopticon.network.SeedPushPacket;

import java.util.*;
import net.minecraft.server.permissions.Permissions;

@Mod.EventBusSubscriber(modid = Panopticon.MODID)
public final class PermsEvents {
    public static final List<PermissionNode<?>> PERM_NODES = new ArrayList<>();
    public static PermissionNode<Boolean> adminNode;

    private PermsEvents() {
    }

    @SubscribeEvent
    public static void onGatherNodes(PermissionGatherEvent.Nodes event) {
        if (Panopticon.CHANNEL == null) {
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
        if (Panopticon.CHANNEL != null && event.getEntity() instanceof ServerPlayer sp) {
            sync(sp);
        }
    }

    public static void sync(ServerPlayer p) {
        if (Panopticon.CHANNEL == null) {
            return;
        }
        List<String> nodes = effectiveNodes(p);
        lastSynced.put(p.getUUID(), nodes);
        Panopticon.CHANNEL.send(new PermsSyncPacket(nodes), PacketDistributor.PLAYER.with(p));
        boolean seedGranted = nodes.contains("panoptic.seed.view");
        long seed = p.level().getServer() != null && p.level().getServer().overworld() != null
                ? p.level().getServer().overworld().getSeed() : 0L;
        Panopticon.CHANNEL.send(new SeedPushPacket(seedGranted, seedGranted ? seed : 0L), PacketDistributor.PLAYER.with(p));
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
    public static void onServerTick(TickEvent.ServerTickEvent.Post event) {
        if (Panopticon.CHANNEL == null || ++tick % 40 != 0) {
            return;
        }
        for (ServerPlayer p : event.server().getPlayerList().getPlayers()) {
            List<String> now = effectiveNodes(p);
            if (!now.equals(lastSynced.get(p.getUUID()))) {
                sync(p);
                event.server().getCommands().sendCommands(p);
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        lastSynced.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        if (Panopticon.CHANNEL != null) {
            PermsCommand.register(event.getDispatcher());
        }
    }
}