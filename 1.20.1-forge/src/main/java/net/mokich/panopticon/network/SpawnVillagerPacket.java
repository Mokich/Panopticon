package net.mokich.panopticon.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraftforge.network.NetworkEvent.Context;
import net.minecraftforge.registries.ForgeRegistries;
import net.mokich.panopticon.perms.PermsStore;

import java.util.function.Supplier;

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

    public static void handle(SpawnVillagerPacket msg, Supplier<Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp == null || !PermsStore.resolve(sp, "panoptic.trade.spawn")) {
                return;
            }
            ServerLevel world = sp.serverLevel();
            AbstractVillager ent;
            String label;
            if (msg.wandering) {
                ent = EntityType.WANDERING_TRADER.create(world);
                label = "wandering_trader";
                if (ent != null) {
                    fill(ent, VillagerTrades.WANDERING_TRADER_TRADES.get(1));
                    fill(ent, VillagerTrades.WANDERING_TRADER_TRADES.get(2));
                }
            } else {
                ResourceLocation rl = ResourceLocation.tryParse(msg.professionId);
                VillagerProfession prof = rl == null ? null : ForgeRegistries.VILLAGER_PROFESSIONS.getValue(rl);
                if (prof == null) {
                    return;
                }
                Villager v = EntityType.VILLAGER.create(world);
                if (v != null) {
                    v.setVillagerData(v.getVillagerData().setProfession(prof).setLevel(5));
                    v.setVillagerXp(9999);
                    var byLevel = VillagerTrades.TRADES.get(prof);
                    if (byLevel != null) {
                        for (int lvl = 1; lvl <= 5; lvl++) {
                            fill(v, byLevel.get(lvl));
                        }
                    }
                }
                ent = v;
                label = rl.getPath();
            }
            if (ent == null) {
                return;
            }
            ent.moveTo(sp.getX(), sp.getY(), sp.getZ(), sp.getYRot(), 0.0F);
            ent.setCustomName(Component.literal(label));
            ent.setPersistenceRequired();
            world.addFreshEntity(ent);
        });
        ctx.get().setPacketHandled(true);
    }

    private static void fill(AbstractVillager ent, VillagerTrades.ItemListing[] listings) {
        if (listings == null) {
            return;
        }
        MerchantOffers offers = ent.getOffers();
        for (VillagerTrades.ItemListing l : listings) {
            if (offers.size() >= 200) {
                return;
            }
            try {
                MerchantOffer o = l.getOffer(ent, ent.getRandom());
                if (o != null) {
                    offers.add(o);
                }
            } catch (Throwable ignored) {
            }
        }
    }
}