package net.mokich.panopticon.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent.Context;
import net.mokich.panopticon.perms.PermsStore;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class GiveRequestPacket {
    public final List<ItemStack> items;

    public GiveRequestPacket(List<ItemStack> items) {
        this.items = items;
    }

    public static void encode(GiveRequestPacket msg, FriendlyByteBuf buf) {
        int n = Math.min(msg.items.size(), 27);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeItem(msg.items.get(i));
        }
    }

    public static GiveRequestPacket decode(FriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 27);
        List<ItemStack> items = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            items.add(buf.readItem());
        }
        return new GiveRequestPacket(items);
    }

    public static void handle(GiveRequestPacket msg, Supplier<Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp == null || !PermsStore.resolve(sp, "panoptic.screens.give")) {
                return;
            }
            for (ItemStack s : msg.items) {
                if (s.isEmpty()) {
                    continue;
                }
                ItemStack c = s.copy();
                if (c.getCount() > c.getMaxStackSize()) {
                    c.setCount(c.getMaxStackSize());
                }
                sp.getInventory().placeItemBackInInventory(c);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}