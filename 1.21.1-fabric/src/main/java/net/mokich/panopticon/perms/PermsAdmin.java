package net.mokich.panopticon.perms;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.mokich.panopticon.Panopticon;
import net.mokich.panopticon.network.AdminStatePacket;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class PermsAdmin {
    public static final byte OP_GROUP_CREATE = 0;
    public static final byte OP_GROUP_REMOVE = 1;
    public static final byte OP_GROUP_NODE_ADD = 2;
    public static final byte OP_GROUP_NODE_REMOVE = 3;
    public static final byte OP_PLAYER_GROUP = 4;
    public static final byte OP_PLAYER_ALLOW = 5;
    public static final byte OP_PLAYER_DENY = 6;
    public static final byte OP_PLAYER_UNSET = 7;
    public static final byte OP_IMPORT = 8;

    private PermsAdmin() {
    }

    public static boolean isAdmin(ServerPlayer p) {
        return p.hasPermissions(3) || PermsStore.adminGranted(p.getUUID()) || PermsApiHook.check(p, "panoptic.admin");
    }

    public static void sendState(ServerPlayer to) {
        MinecraftServer server = to.getServer();
        if (server == null || !Panopticon.ACTIVE) {
            return;
        }
        List<AdminStatePacket.PlayerRow> rows = new ArrayList<>();
        Set<UUID> seen = new HashSet<>();
        for (PermsStore.PlayerSnap snap : PermsStore.snapshotPlayers()) {
            seen.add(snap.id());
            String name = server.getProfileCache() == null ? null
                    : server.getProfileCache().get(snap.id()).map(GameProfile::getName).orElse(null);
            ServerPlayer online = server.getPlayerList().getPlayer(snap.id());
            if (online != null) {
                name = online.getGameProfile().getName();
            }
            rows.add(new AdminStatePacket.PlayerRow(snap.id(),
                    name == null ? snap.id().toString().substring(0, 8) : name,
                    snap.group(), snap.allow(), snap.deny()));
        }
        for (ServerPlayer online : server.getPlayerList().getPlayers()) {
            if (seen.add(online.getUUID())) {
                rows.add(new AdminStatePacket.PlayerRow(online.getUUID(), online.getGameProfile().getName(),
                        "", new HashSet<>(), new HashSet<>()));
            }
        }
        ServerPlayNetworking.send(to, new AdminStatePacket(PermsStore.snapshotGroups(), rows, PermsStore.exportJson()));
    }

    public static void applyEdit(ServerPlayer sender, byte op, String a, String b) {
        switch (op) {
            case OP_GROUP_CREATE -> {
                if (a.matches("[a-zA-Z0-9_-]{1,32}")) {
                    PermsStore.createGroup(a);
                }
            }
            case OP_GROUP_REMOVE -> PermsStore.removeGroup(a);
            case OP_GROUP_NODE_ADD -> PermsStore.setGroupNode(a, b, true);
            case OP_GROUP_NODE_REMOVE -> PermsStore.setGroupNode(a, b, false);
            case OP_PLAYER_GROUP -> {
                UUID id = parse(a);
                if (id != null && (b.isEmpty() || PermsStore.groupNames().contains(b))) {
                    PermsStore.setPlayerGroup(id, b.isEmpty() ? PermsStore.DEFAULT_GROUP : b);
                }
            }
            case OP_PLAYER_ALLOW -> {
                UUID id = parse(a);
                if (id != null) {
                    PermsStore.setPlayerNode(id, b, 1);
                }
            }
            case OP_PLAYER_DENY -> {
                UUID id = parse(a);
                if (id != null) {
                    PermsStore.setPlayerNode(id, b, -1);
                }
            }
            case OP_PLAYER_UNSET -> {
                UUID id = parse(a);
                if (id != null) {
                    PermsStore.setPlayerNode(id, b, 0);
                }
            }
            case OP_IMPORT -> PermsStore.importJson(a);
            default -> {
                return;
            }
        }
        MinecraftServer server = sender.getServer();
        if (server != null) {
            PermsEvents.resyncAll(server);
        }
        sendState(sender);
    }

    private static UUID parse(String s) {
        try {
            return UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}