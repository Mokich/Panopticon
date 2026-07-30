package net.mokich.panopticon.trade;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.TickEvent;
import net.mokich.panopticon.Panopticon;

import java.util.ArrayDeque;
import java.util.Deque;

@Mod.EventBusSubscriber(modid = Panopticon.MODID)
public final class TradeWarmup {
    private static final Deque<String> QUEUE = new ArrayDeque<>();
    private static boolean wanderingQueued;

    private TradeWarmup() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        QUEUE.clear();
        wanderingQueued = true;
        for (VillagerProfession prof : BuiltInRegistries.VILLAGER_PROFESSION) {
            Identifier id = BuiltInRegistries.VILLAGER_PROFESSION.getKey(prof);
            if (id != null) {
                QUEUE.add(id.toString());
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent.Post event) {
        if (!wanderingQueued && QUEUE.isEmpty()) {
            return;
        }
        MinecraftServer server = event.server();
        ServerLevel world = server == null ? null : server.overworld();
        if (world == null) {
            return;
        }
        try {
            if (wanderingQueued) {
                wanderingQueued = false;
                TradeSampler.sample(world, "minecraft:wandering_trader", true);
                return;
            }
            String id = QUEUE.poll();
            if (id != null) {
                TradeSampler.sample(world, id, false);
            }
        } catch (Throwable t) {
            QUEUE.clear();
            wanderingQueued = false;
        }
    }
}