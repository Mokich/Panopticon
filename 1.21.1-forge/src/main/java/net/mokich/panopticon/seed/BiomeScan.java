package net.mokich.panopticon.seed;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraftforge.network.PacketDistributor;
import net.mokich.panopticon.Panopticon;
import net.mokich.panopticon.network.BiomeTilePackets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class BiomeScan {
    public static final int RES = 128;
    private static final int MAX_PENDING = 64;
    private static final int SEA_Y = QuartPos.fromBlock(63);

    public record Payload(int[] palette, byte[] grid) {}

    private static final Map<String, Payload> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Payload> e) {
                    return size() > 1024;
                }
            });
    private static final Map<String, List<UUID>> WAITERS = new ConcurrentHashMap<>();
    private static final ExecutorService POOL = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "panopticon-biome-scan");
        t.setDaemon(true);
        return t;
    });

    private BiomeScan() {
    }

    public static void request(ServerPlayer sp, String dim, int step, int tx, int tz) {
        if (Integer.bitCount(step) != 1 || step < 4 || step > 512) {
            return;
        }
        long tw = (long) RES * step;
        if (Math.abs((long) tx * tw) > 40_000_000L || Math.abs((long) tz * tw) > 40_000_000L) {
            return;
        }
        String key = dim + "|" + step + "|" + tx + "|" + tz;
        Payload cached = CACHE.get(key);
        if (cached != null) {
            send(sp, dim, step, tx, tz, cached);
            return;
        }
        ServerLevel level = RegionScan.levelFor(sp.server, dim);
        if (level == null) {
            return;
        }
        List<UUID> waiters = WAITERS.get(key);
        if (waiters != null) {
            synchronized (waiters) {
                if (!waiters.contains(sp.getUUID())) {
                    waiters.add(sp.getUUID());
                }
            }
            return;
        }
        if (WAITERS.size() >= MAX_PENDING) {
            return;
        }
        List<UUID> list = new ArrayList<>();
        list.add(sp.getUUID());
        WAITERS.put(key, list);
        MinecraftServer server = sp.server;
        BiomeSource source = level.getChunkSource().getGenerator().getBiomeSource();
        RandomState rs = level.getChunkSource().randomState();
        Registry<Biome> reg = level.registryAccess().registryOrThrow(Registries.BIOME);
        POOL.submit(() -> {
            Payload payload;
            try {
                payload = sample(source, rs, reg, step, tx, tz);
            } catch (Throwable x) {
                WAITERS.remove(key);
                return;
            }
            server.execute(() -> {
                CACHE.put(key, payload);
                List<UUID> w = WAITERS.remove(key);
                if (w == null) {
                    return;
                }
                for (UUID id : w) {
                    ServerPlayer target = server.getPlayerList().getPlayer(id);
                    if (target != null) {
                        send(target, dim, step, tx, tz, payload);
                    }
                }
            });
        });
    }

    private static Payload sample(BiomeSource source, RandomState rs, Registry<Biome> reg,
            int step, int tx, int tz) {
        int tw = RES * step;
        byte[] grid = new byte[RES * RES];
        Map<Integer, Integer> palIdx = new LinkedHashMap<>();
        for (int pz = 0; pz < RES; pz++) {
            for (int px = 0; px < RES; px++) {
                int wx = tx * tw + px * step;
                int wz = tz * tw + pz * step;
                Holder<Biome> b = source.getNoiseBiome(
                        QuartPos.fromBlock(wx), SEA_Y, QuartPos.fromBlock(wz), rs.sampler());
                int id = reg.getId(b.value());
                Integer pi = palIdx.get(id);
                if (pi == null) {
                    if (palIdx.size() >= 256) {
                        pi = 0;
                    } else {
                        pi = palIdx.size();
                        palIdx.put(id, pi);
                    }
                }
                grid[pz * RES + px] = (byte) (int) pi;
            }
        }
        int[] palette = new int[palIdx.size()];
        int i = 0;
        for (int id : palIdx.keySet()) {
            palette[i++] = id;
        }
        return new Payload(palette, grid);
    }

    private static void send(ServerPlayer to, String dim, int step, int tx, int tz, Payload p) {
        if (Panopticon.CHANNEL != null) {
            Panopticon.CHANNEL.send(new BiomeTilePackets.Result(dim, step, tx, tz, p.palette(), p.grid()), PacketDistributor.PLAYER.with(to));
        }
    }
}