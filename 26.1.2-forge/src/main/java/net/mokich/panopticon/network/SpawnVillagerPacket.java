package net.mokich.panopticon.network;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.item.trading.TradeSets;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.mokich.panopticon.perms.PermsStore;

import java.lang.reflect.Method;

public class SpawnVillagerPacket {

    public final String professionId;
    public final boolean wandering;

    public SpawnVillagerPacket(String professionId, boolean wandering) {
        this.professionId = professionId;
        this.wandering = wandering;
    }

    public static void encode(SpawnVillagerPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.professionId, 128);
        buf.writeBoolean(msg.wandering);
    }

    public static SpawnVillagerPacket decode(FriendlyByteBuf buf) {
        return new SpawnVillagerPacket(buf.readUtf(128), buf.readBoolean());
    }

    public static void handle(SpawnVillagerPacket msg, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.getSender() instanceof ServerPlayer sp)
                    || !PermsStore.resolve(sp, "panoptic.trade.spawn")) {
                return;
            }
            ServerLevel world = sp.level();
            AbstractVillager ent;
            String label;
            if (msg.wandering) {
                ent = EntityType.WANDERING_TRADER.create(world, EntitySpawnReason.COMMAND);
                label = "wandering_trader";
                if (ent != null) {
                    fill(ent, world, TradeSets.WANDERING_TRADER_BUYING);
                    fill(ent, world, TradeSets.WANDERING_TRADER_COMMON);
                    fill(ent, world, TradeSets.WANDERING_TRADER_UNCOMMON);
                }
            } else {
                Identifier rl = Identifier.tryParse(msg.professionId);
                VillagerProfession prof = rl == null
                        ? null : BuiltInRegistries.VILLAGER_PROFESSION.getOptional(rl).orElse(null);
                if (prof == null) {
                    return;
                }
                Villager v = EntityType.VILLAGER.create(world, EntitySpawnReason.COMMAND);
                if (v != null) {
                    Holder<VillagerProfession> ph = BuiltInRegistries.VILLAGER_PROFESSION.wrapAsHolder(prof);
                    v.setVillagerData(new VillagerData(v.getVillagerData().type(), ph, 5));
                    v.setVillagerXp(9999);
                    for (int lvl = 1; lvl <= 5; lvl++) {
                        fill(v, world, prof.getTrades(lvl));
                    }
                }
                ent = v;
                label = rl.getPath();
            }
            if (ent == null) {
                return;
            }
            ent.snapTo(sp.getX(), sp.getY(), sp.getZ(), sp.getYRot(), 0.0F);
            ent.setCustomName(Component.literal(label));
            ent.setPersistenceRequired();
            world.addFreshEntity(ent);
        });
    }

    private static final Method ADD_OFFERS = resolveAddOffers();

    private static Method resolveAddOffers() {
        for (Method m : AbstractVillager.class.getDeclaredMethods()) {
            Class<?>[] p = m.getParameterTypes();
            if (p.length == 3 && p[0] == ServerLevel.class
                    && p[1] == MerchantOffers.class && p[2] == ResourceKey.class) {
                try {
                    m.setAccessible(true);
                    return m;
                } catch (Throwable t) {
                    return null;
                }
            }
        }
        return null;
    }

    private static void fill(AbstractVillager ent, ServerLevel world, ResourceKey<TradeSet> set) {
        if (ADD_OFFERS == null || set == null) {
            return;
        }
        MerchantOffers offers = ent.getOffers();
        if (offers.size() >= 200) {
            return;
        }
        try {
            ADD_OFFERS.invoke(ent, world, offers, set);
        } catch (Throwable ignored) {
        }
    }
}