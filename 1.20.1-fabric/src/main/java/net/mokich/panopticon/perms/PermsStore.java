package net.mokich.panopticon.perms;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class PermsStore {
    public static final List<String> NODES = List.of(
            "panoptic.inspector",
            "panoptic.seed.view",
            "panoptic.seed.structures",
            "panoptic.trade",
            "panoptic.trade.spawn",
            "panoptic.screens",
            "panoptic.screens.give");
    public static final String ADMIN_NODE = "panoptic.admin";
    public static final List<String> PLAYER_NODES = List.of(
            "panoptic.inspector",
            "panoptic.seed.view",
            "panoptic.seed.structures",
            "panoptic.trade",
            "panoptic.trade.spawn",
            "panoptic.screens",
            "panoptic.screens.give",
            ADMIN_NODE);
    public static final String DEFAULT_GROUP = "default";

    public static final class PlayerEntry {
        String group;
        final Set<String> allow = new LinkedHashSet<>();
        final Set<String> deny = new LinkedHashSet<>();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Map<String, Set<String>> groups;
    private static Map<UUID, PlayerEntry> players;

    private PermsStore() {
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("panoptic").resolve("perms.json");
    }

    public static synchronized void load() {
        groups = new LinkedHashMap<>();
        players = new HashMap<>();
        groups.put(DEFAULT_GROUP, new LinkedHashSet<>());
        try {
            Path p = file();
            if (!Files.exists(p)) {
                save();
                return;
            }
            JsonObject root = JsonParser.parseString(Files.readString(p)).getAsJsonObject();
            JsonObject g = root.getAsJsonObject("groups");
            if (g != null) {
                for (String name : g.keySet()) {
                    Set<String> nodes = new LinkedHashSet<>();
                    for (JsonElement e : g.getAsJsonArray(name)) {
                        if (NODES.contains(e.getAsString())) {
                            nodes.add(e.getAsString());
                        }
                    }
                    groups.put(name, nodes);
                }
            }
            groups.computeIfAbsent(DEFAULT_GROUP, k -> new LinkedHashSet<>());
            JsonObject pl = root.getAsJsonObject("players");
            if (pl != null) {
                for (String id : pl.keySet()) {
                    UUID uuid;
                    try {
                        uuid = UUID.fromString(id);
                    } catch (IllegalArgumentException x) {
                        continue;
                    }
                    JsonObject o = pl.getAsJsonObject(id);
                    PlayerEntry e = new PlayerEntry();
                    if (o.has("group")) {
                        e.group = o.get("group").getAsString();
                    }
                    if (o.has("allow")) {
                        for (JsonElement n : o.getAsJsonArray("allow")) {
                            if (PLAYER_NODES.contains(n.getAsString())) {
                                e.allow.add(n.getAsString());
                            }
                        }
                    }
                    if (o.has("deny")) {
                        for (JsonElement n : o.getAsJsonArray("deny")) {
                            if (PLAYER_NODES.contains(n.getAsString())) {
                                e.deny.add(n.getAsString());
                            }
                        }
                    }
                    players.put(uuid, e);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public static synchronized void save() {
        try {
            JsonObject root = new JsonObject();
            JsonObject g = new JsonObject();
            for (Map.Entry<String, Set<String>> e : groups.entrySet()) {
                JsonArray arr = new JsonArray();
                e.getValue().forEach(arr::add);
                g.add(e.getKey(), arr);
            }
            root.add("groups", g);
            JsonObject pl = new JsonObject();
            for (Map.Entry<UUID, PlayerEntry> e : players.entrySet()) {
                JsonObject o = new JsonObject();
                if (e.getValue().group != null) {
                    o.addProperty("group", e.getValue().group);
                }
                JsonArray allow = new JsonArray();
                e.getValue().allow.forEach(allow::add);
                JsonArray deny = new JsonArray();
                e.getValue().deny.forEach(deny::add);
                if (!allow.isEmpty()) {
                    o.add("allow", allow);
                }
                if (!deny.isEmpty()) {
                    o.add("deny", deny);
                }
                pl.add(e.getKey().toString(), o);
            }
            root.add("players", pl);
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(root));
        } catch (Throwable ignored) {
        }
    }

    private static synchronized void ensure() {
        if (groups == null) {
            load();
        }
    }

    public static synchronized boolean resolve(ServerPlayer player, String node) {
        ensure();
        if (player.hasPermissions(2)) {
            return true;
        }
        PlayerEntry e = players.get(player.getUUID());
        if (e != null) {
            if (e.deny.contains(node)) {
                return false;
            }
            if (e.allow.contains(node)) {
                return true;
            }
        }
        String groupName = e != null && e.group != null ? e.group : DEFAULT_GROUP;
        Set<String> nodes = groups.get(groupName);
        if (nodes == null) {
            nodes = groups.get(DEFAULT_GROUP);
        }
        return nodes != null && nodes.contains(node);
    }

    public static synchronized List<String> nodesFor(ServerPlayer player) {
        List<String> out = new ArrayList<>();
        for (String n : NODES) {
            if (resolve(player, n)) {
                out.add(n);
            }
        }
        return out;
    }

    public static synchronized boolean adminGranted(UUID id) {
        ensure();
        PlayerEntry e = players.get(id);
        return e != null && e.allow.contains(ADMIN_NODE) && !e.deny.contains(ADMIN_NODE);
    }

    public static synchronized Set<String> groupNames() {
        ensure();
        return new LinkedHashSet<>(groups.keySet());
    }

    public static synchronized boolean createGroup(String name) {
        ensure();
        if (groups.containsKey(name)) {
            return false;
        }
        groups.put(name, new LinkedHashSet<>());
        save();
        return true;
    }

    public static synchronized boolean removeGroup(String name) {
        ensure();
        if (DEFAULT_GROUP.equals(name) || groups.remove(name) == null) {
            return false;
        }
        for (PlayerEntry e : players.values()) {
            if (name.equals(e.group)) {
                e.group = null;
            }
        }
        save();
        return true;
    }

    public static synchronized boolean setGroupNode(String group, String node, boolean add) {
        ensure();
        Set<String> nodes = groups.get(group);
        if (nodes == null || !NODES.contains(node)) {
            return false;
        }
        boolean changed = add ? nodes.add(node) : nodes.remove(node);
        if (changed) {
            save();
        }
        return changed;
    }

    public static synchronized void setPlayerGroup(UUID id, String group) {
        ensure();
        PlayerEntry e = players.computeIfAbsent(id, k -> new PlayerEntry());
        e.group = DEFAULT_GROUP.equals(group) ? null : group;
        cleanup(id, e);
        save();
    }

    public static synchronized boolean setPlayerNode(UUID id, String node, int mode) {
        ensure();
        if (!PLAYER_NODES.contains(node)) {
            return false;
        }
        PlayerEntry e = players.computeIfAbsent(id, k -> new PlayerEntry());
        e.allow.remove(node);
        e.deny.remove(node);
        if (mode > 0) {
            e.allow.add(node);
        } else if (mode < 0) {
            e.deny.add(node);
        }
        cleanup(id, e);
        save();
        return true;
    }

    private static void cleanup(UUID id, PlayerEntry e) {
        if (e.group == null && e.allow.isEmpty() && e.deny.isEmpty()) {
            players.remove(id);
        }
    }

    public record PlayerSnap(UUID id, String group, Set<String> allow, Set<String> deny) {}

    public static synchronized Map<String, Set<String>> snapshotGroups() {
        ensure();
        Map<String, Set<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> e : groups.entrySet()) {
            out.put(e.getKey(), new LinkedHashSet<>(e.getValue()));
        }
        return out;
    }

    public static synchronized List<PlayerSnap> snapshotPlayers() {
        ensure();
        List<PlayerSnap> out = new ArrayList<>();
        for (Map.Entry<UUID, PlayerEntry> e : players.entrySet()) {
            PlayerEntry pe = e.getValue();
            out.add(new PlayerSnap(e.getKey(), pe.group == null ? "" : pe.group,
                    new LinkedHashSet<>(pe.allow), new LinkedHashSet<>(pe.deny)));
        }
        return out;
    }

    public static synchronized String describe(ServerPlayer player) {
        StringBuilder sb = new StringBuilder();
        for (String n : NODES) {
            sb.append(n).append(" = ").append(resolve(player, n)).append('\n');
        }
        return sb.toString();
    }

    public static synchronized String exportJson() {
        ensure();
        save();
        try {
            return Files.readString(file());
        } catch (Throwable t) {
            return "{}";
        }
    }

    public static synchronized boolean importJson(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!root.has("groups")) {
                return false;
            }
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(root));
            load();
            return true;
        } catch (Throwable t) {
            return false;
        }
    }
}