package net.mokich.panopticon.trade;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.entity.npc.villager.VillagerType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.TradeCost;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.item.trading.TradeSets;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.functions.ExplorationMapFunction;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;
import net.minecraft.world.level.storage.loot.functions.SetNameFunction;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.mokich.panopticon.network.TradeBookPackets;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class TradeSampler {
    private static final int MAX_SEEDS = 400;
    private static final int MAX_OFFERS = 160;
    private static final long BUDGET_NANOS = 800_000_000L;
    private static final Map<String, List<TradeBookPackets.Entry>> CACHE = new LinkedHashMap<>();
    private static Object cacheOwner;

    private TradeSampler() {
    }

    public static synchronized List<TradeBookPackets.Entry> sample(ServerLevel world, String professionId,
                                                                   boolean wandering) {
        if (world == null) {
            return List.of();
        }
        if (cacheOwner != world.getServer()) {
            CACHE.clear();
            cacheOwner = world.getServer();
        }
        String key = (wandering ? "w|" : "p|") + professionId;
        List<TradeBookPackets.Entry> hit = CACHE.get(key);
        if (hit != null) {
            return hit;
        }
        List<TradeBookPackets.Entry> built = build(world, professionId, wandering);
        CACHE.put(key, built);
        return built;
    }

    private static List<TradeBookPackets.Entry> build(ServerLevel world, String professionId, boolean wandering) {
        List<TradeBookPackets.Entry> out = new ArrayList<>();
        Registry<TradeSet> sets = world.registryAccess().lookup(Registries.TRADE_SET).orElse(null);
        if (sets == null) {
            return out;
        }
        Map<Integer, List<ResourceKey<TradeSet>>> byLevel = new LinkedHashMap<>();
        String sourceMod = "minecraft";
        if (wandering) {
            byLevel.computeIfAbsent(1, k -> new ArrayList<>()).add(TradeSets.WANDERING_TRADER_BUYING);
            byLevel.computeIfAbsent(1, k -> new ArrayList<>()).add(TradeSets.WANDERING_TRADER_COMMON);
            byLevel.computeIfAbsent(2, k -> new ArrayList<>()).add(TradeSets.WANDERING_TRADER_UNCOMMON);
        } else {
            Identifier id = Identifier.tryParse(professionId);
            VillagerProfession prof = id == null
                    ? null : BuiltInRegistries.VILLAGER_PROFESSION.getOptional(id).orElse(null);
            if (prof == null) {
                return out;
            }
            sourceMod = id.getNamespace();
            for (int lvl = 1; lvl <= 5; lvl++) {
                ResourceKey<TradeSet> key = prof.getTrades(lvl);
                if (key != null) {
                    byLevel.computeIfAbsent(lvl, k -> new ArrayList<>()).add(key);
                }
            }
        }
        if (byLevel.isEmpty()) {
            return out;
        }
        AbstractVillager ent = wandering
                ? EntityType.WANDERING_TRADER.create(world, EntitySpawnReason.COMMAND)
                : EntityType.VILLAGER.create(world, EntitySpawnReason.COMMAND);
        if (ent == null) {
            return out;
        }
        try {
            BlockPos at = world.getRespawnData().pos();
            ent.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
            List<Holder<VillagerType>> types = variantHolders(ent);
            long deadline = System.nanoTime() + BUDGET_NANOS;
            for (Map.Entry<Integer, List<ResourceKey<TradeSet>>> e : byLevel.entrySet()) {
                for (ResourceKey<TradeSet> key : e.getValue()) {
                    TradeSet ts = sets.getValue(key);
                    if (ts == null) {
                        continue;
                    }
                    for (Holder<VillagerTrade> th : ts.getTrades()) {
                        VillagerTrade trade = th.value();
                        List<TradeBookPackets.Offer> offers =
                                collect(trade, ent, world, types, deadline);
                        if (!offers.isEmpty()) {
                            out.add(new TradeBookPackets.Entry(e.getKey(), sourceMod, offers));
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        } finally {
            ent.discard();
        }
        return out;
    }

    private static List<TradeBookPackets.Offer> collect(VillagerTrade trade, AbstractVillager ent,
                                                        ServerLevel world, List<Holder<VillagerType>> types,
                                                        long deadline) {
        if (locatesStructure(trade)) {
            TradeBookPackets.Offer base = staticOffer(trade);
            return base == null ? List.of() : List.of(base);
        }
        Map<String, TradeBookPackets.Offer> distinct = new LinkedHashMap<>();
        int passes = isVariantGated(trade) ? Math.max(1, types.size()) : 1;
        for (int vi = 0; vi < passes; vi++) {
            applyVariant(ent, types, vi);
            LootParams params = new LootParams.Builder(world)
                    .withParameter(LootContextParams.ORIGIN, ent.position())
                    .withParameter(LootContextParams.THIS_ENTITY, ent)
                    .withParameter(LootContextParams.ADDITIONAL_COST_COMPONENT_ALLOWED, Unit.INSTANCE)
                    .create(LootContextParamSets.VILLAGER_TRADE);
            int sinceNew = 0;
            for (long seed = 1; seed <= MAX_SEEDS && sinceNew < 60 && distinct.size() < MAX_OFFERS
                    && System.nanoTime() < deadline; seed++) {
                MerchantOffer o = trySample(trade, params, seed * 7919L);
                if (o == null || o.getResult().isEmpty()) {
                    continue;
                }
                String k = key(o);
                if (distinct.containsKey(k)) {
                    sinceNew++;
                } else {
                    sinceNew = 0;
                    distinct.put(k, new TradeBookPackets.Offer(o.getBaseCostA().copy(), o.getCostB().copy(),
                            o.getResult().copy(), o.getMaxUses(), o.getXp()));
                }
            }
        }
        return new ArrayList<>(distinct.values());
    }

    private static MerchantOffer trySample(VillagerTrade trade, LootParams params, long seed) {
        try {
            LootContext ctx = new LootContext.Builder(params).withOptionalRandomSeed(seed).create(Optional.empty());
            return trade.getOffer(ctx);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String key(MerchantOffer o) {
        return o.getResult().getItem() + "|" + o.getResult().getCount() + "|"
                + o.getResult().getComponentsPatch() + "|"
                + o.getBaseCostA().getItem() + "|" + o.getBaseCostA().getCount() + "|"
                + o.getCostB().getItem() + "|" + o.getCostB().getCount();
    }

    private static TradeBookPackets.Offer staticOffer(VillagerTrade trade) {
        ItemStack result = ItemStack.EMPTY;
        ItemStack costA = ItemStack.EMPTY;
        ItemStack costB = ItemStack.EMPTY;
        for (Field f : VillagerTrade.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers())) {
                continue;
            }
            try {
                if (!f.trySetAccessible()) {
                    continue;
                }
                Object v = f.get(trade);
                if (v instanceof ItemStackTemplate tpl && result.isEmpty()) {
                    result = tpl.create();
                } else if (v instanceof TradeCost tc && costA.isEmpty()) {
                    costA = costStack(tc);
                } else if (v instanceof Optional<?> opt && opt.isPresent()
                        && opt.get() instanceof TradeCost tc2 && costB.isEmpty()) {
                    costB = costStack(tc2);
                }
            } catch (Throwable ignored) {
            }
        }
        if (result.isEmpty()) {
            return null;
        }
        if (result.is(Items.MAP)) {
            ItemStack filled = new ItemStack(Items.FILLED_MAP, result.getCount());
            filled.applyComponents(result.getComponentsPatch());
            result = filled;
        }
        Component name = modifierName(trade);
        if (name != null) {
            result.set(DataComponents.ITEM_NAME, name);
        }
        return new TradeBookPackets.Offer(costA, costB, result, 12, 0);
    }

    private static ItemStack costStack(TradeCost tc) {
        try {
            return new ItemStack(tc.item().value(), Math.max(1, Math.round(tc.count().getFloat(null))));
        } catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }

    private static Component modifierName(VillagerTrade trade) {
        for (LootItemFunction fn : modifiersOf(trade)) {
            if (!(fn instanceof SetNameFunction)) {
                continue;
            }
            for (Field f : SetNameFunction.class.getDeclaredFields()) {
                if (Modifier.isStatic(f.getModifiers()) || f.getType() != Optional.class) {
                    continue;
                }
                try {
                    if (!f.trySetAccessible()) {
                        continue;
                    }
                    if (f.get(fn) instanceof Optional<?> o && o.isPresent() && o.get() instanceof Component c) {
                        return c;
                    }
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static boolean locatesStructure(VillagerTrade trade) {
        for (LootItemFunction fn : modifiersOf(trade)) {
            if (fn instanceof ExplorationMapFunction) {
                return true;
            }
        }
        return false;
    }

    private static boolean isVariantGated(VillagerTrade trade) {
        for (Field f : VillagerTrade.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || f.getType() != Optional.class
                    || !genericArgIs(f, LootItemCondition.class)) {
                continue;
            }
            try {
                if (!f.trySetAccessible()) {
                    continue;
                }
                return f.get(trade) instanceof Optional<?> o && o.isPresent();
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static List<LootItemFunction> modifiersOf(VillagerTrade trade) {
        if (trade == null) {
            return List.of();
        }
        for (Field f : VillagerTrade.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) || !List.class.isAssignableFrom(f.getType())) {
                continue;
            }
            try {
                if (!f.trySetAccessible()) {
                    continue;
                }
                if (!(f.get(trade) instanceof List<?> raw)) {
                    continue;
                }
                List<LootItemFunction> res = new ArrayList<>();
                for (Object o : raw) {
                    if (o instanceof LootItemFunction fn) {
                        res.add(fn);
                    }
                }
                return res;
            } catch (Throwable ignored) {
            }
        }
        return List.of();
    }

    private static boolean genericArgIs(Field f, Class<?> want) {
        if (!(f.getGenericType() instanceof ParameterizedType pt)) {
            return false;
        }
        Type[] args = pt.getActualTypeArguments();
        return args.length == 1 && args[0] instanceof Class<?> c && want.isAssignableFrom(c);
    }

    private static List<Holder<VillagerType>> variantHolders(AbstractVillager ent) {
        if (!(ent instanceof Villager)) {
            return List.of();
        }
        List<Holder<VillagerType>> out = new ArrayList<>();
        try {
            out.addAll(BuiltInRegistries.VILLAGER_TYPE.listElements().toList());
        } catch (Throwable ignored) {
        }
        return out;
    }

    private static void applyVariant(AbstractVillager ent, List<Holder<VillagerType>> types, int index) {
        if (!(ent instanceof Villager v) || index >= types.size()) {
            return;
        }
        try {
            v.setVillagerData(v.getVillagerData().withType(types.get(index)));
        } catch (Throwable ignored) {
        }
    }
}