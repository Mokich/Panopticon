package net.mokich.panopticon.seed;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class RegionScan {
    public record Found(String id, int x, int z) {}

    private record Candidate(ChunkPos pos, List<StructureSet.StructureSelectionEntry> ordered) {}

    private static final Map<String, List<Found>> CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(128, 0.75F, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, List<Found>> e) {
                    return size() > 1024;
                }
            });
    private static final AtomicInteger ACTIVE = new AtomicInteger();
    private static final ConcurrentLinkedQueue<Runnable> QUEUE = new ConcurrentLinkedQueue<>();
    private static final int MAX_ACTIVE = 1;
    private static final int MAX_QUEUE = 48;

    private RegionScan() {
    }

    public static void compute(ServerLevel sl, int rx, int rz, Consumer<List<Found>> done) {
        String key = sl.dimension().location() + "|" + rx + "|" + rz;
        List<Found> cached = CACHE.get(key);
        if (cached != null) {
            done.accept(cached);
            return;
        }
        if (QUEUE.size() >= MAX_QUEUE) {
            return;
        }
        Runnable start = () -> startCompute(sl, rx, rz, key, done);
        if (ACTIVE.incrementAndGet() <= MAX_ACTIVE) {
            start.run();
        } else {
            ACTIVE.decrementAndGet();
            QUEUE.add(start);
        }
    }

    private static void finish() {
        ACTIVE.decrementAndGet();
        Runnable next = QUEUE.poll();
        if (next != null) {
            ACTIVE.incrementAndGet();
            next.run();
        }
    }

    private static void startCompute(ServerLevel sl, int rx, int rz, String key, Consumer<List<Found>> done) {
        MinecraftServer srv = sl.getServer();
        List<Found> out = new ArrayList<>();
        List<Candidate> filtered = new ArrayList<>();
        try {
            ChunkGeneratorStructureState st = sl.getChunkSource().getGeneratorState();
            int cx0 = rx * 32;
            int cz0 = rz * 32;
            int cx1 = cx0 + 31;
            int cz1 = cz0 + 31;
            for (Holder<StructureSet> setH : st.possibleStructureSets()) {
                StructureSet set = setH.value();
                StructurePlacement pl = set.placement();
                if (set.structures().isEmpty()) {
                    continue;
                }
                if (pl instanceof ConcentricRingsStructurePlacement crsp) {
                    List<ChunkPos> rings = st.getRingPositionsFor(crsp);
                    if (rings != null) {
                        for (ChunkPos cp : rings) {
                            if (cp.x >= cx0 && cp.x <= cx1 && cp.z >= cz0 && cp.z <= cz1) {
                                String id = set.structures().get(0).structure().unwrapKey()
                                        .map(k -> k.location().toString()).orElse(null);
                                if (id != null) {
                                    out.add(new Found(id, cp.getMiddleBlockX(), cp.getMiddleBlockZ()));
                                }
                            }
                        }
                    }
                    continue;
                }
                if (pl instanceof RandomSpreadStructurePlacement rsp && rsp.spacing() > 0) {
                    int spacing = rsp.spacing();
                    int sx0 = Math.floorDiv(cx0, spacing);
                    int sx1 = Math.floorDiv(cx1, spacing);
                    int sz0 = Math.floorDiv(cz0, spacing);
                    int sz1 = Math.floorDiv(cz1, spacing);
                    for (int sz = sz0; sz <= sz1; sz++) {
                        for (int sx = sx0; sx <= sx1; sx++) {
                            ChunkPos cp = rsp.getPotentialStructureChunk(st.getLevelSeed(), sx * spacing, sz * spacing);
                            if (cp.x >= cx0 && cp.x <= cx1 && cp.z >= cz0 && cp.z <= cz1
                                    && safePlacement(pl, st, cp)) {
                                filtered.add(new Candidate(cp, ordered(st, set, cp)));
                            }
                        }
                    }
                } else {
                    for (int cz = cz0; cz <= cz1; cz++) {
                        for (int cx = cx0; cx <= cx1; cx++) {
                            ChunkPos cp = new ChunkPos(cx, cz);
                            if (safePlacement(pl, st, cp)) {
                                filtered.add(new Candidate(cp, ordered(st, set, cp)));
                            }
                        }
                    }
                }
            }
        } catch (Throwable t) {
            CACHE.put(key, out);
            done.accept(out);
            finish();
            return;
        }
        srv.execute(new Runnable() {
            private int idx;

            @Override
            public void run() {
                try {
                    long t0 = System.nanoTime();
                    while (idx < filtered.size()) {
                        if (System.nanoTime() - t0 > 2_000_000L) {
                            srv.execute(this);
                            return;
                        }
                        Candidate c = filtered.get(idx++);
                        for (StructureSet.StructureSelectionEntry e : c.ordered()) {
                            Structure s = e.structure().value();
                            boolean present;
                            try {
                                Structure.GenerationContext ctx = new Structure.GenerationContext(
                                        sl.registryAccess(), sl.getChunkSource().getGenerator(),
                                        sl.getChunkSource().getGenerator().getBiomeSource(),
                                        sl.getChunkSource().randomState(),
                                        sl.getServer().getStructureManager(),
                                        sl.getSeed(), c.pos(), sl, s.biomes()::contains);
                                present = s.findValidGenerationPoint(ctx).isPresent();
                            } catch (Throwable x) {
                                continue;
                            }
                            if (!present) {
                                continue;
                            }
                            String id = e.structure().unwrapKey().map(k -> k.location().toString()).orElse(null);
                            if (id != null) {
                                out.add(new Found(id, c.pos().getMiddleBlockX(), c.pos().getMiddleBlockZ()));
                            }
                            break;
                        }
                    }
                    CACHE.put(key, out);
                    done.accept(out);
                    finish();
                } catch (Throwable t) {
                    CACHE.put(key, out);
                    done.accept(out);
                    finish();
                }
            }
        });
    }

    private static boolean safePlacement(StructurePlacement pl, ChunkGeneratorStructureState st, ChunkPos cp) {
        try {
            return pl.isStructureChunk(st, cp.x, cp.z);
        } catch (Throwable t) {
            return false;
        }
    }

    private static List<StructureSet.StructureSelectionEntry> ordered(
            ChunkGeneratorStructureState st, StructureSet set, ChunkPos cp) {
        List<StructureSet.StructureSelectionEntry> list = set.structures();
        if (list.size() == 1) {
            return list;
        }
        List<StructureSet.StructureSelectionEntry> pool = new ArrayList<>(list);
        List<StructureSet.StructureSelectionEntry> order = new ArrayList<>(list.size());
        WorldgenRandom rnd = new WorldgenRandom(new LegacyRandomSource(0L));
        rnd.setLargeFeatureSeed(st.getLevelSeed(), cp.x, cp.z);
        int total = 0;
        for (StructureSet.StructureSelectionEntry e : pool) {
            total += e.weight();
        }
        while (!pool.isEmpty() && total > 0) {
            int roll = rnd.nextInt(total);
            int idx = 0;
            for (StructureSet.StructureSelectionEntry e : pool) {
                roll -= e.weight();
                if (roll < 0) {
                    break;
                }
                idx++;
            }
            StructureSet.StructureSelectionEntry picked = pool.remove(idx);
            order.add(picked);
            total -= picked.weight();
        }
        return order;
    }

    public static ServerLevel levelFor(MinecraftServer server, String dimId) {
        ResourceLocation rl = ResourceLocation.tryParse(dimId);
        if (rl == null) {
            return null;
        }
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, rl));
    }
}