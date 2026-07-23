package net.mokich.panopticon.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.mokich.panopticon.perms.PermsStore;

import java.util.ArrayList;
import java.util.List;

public class GiveRequestPacket implements CustomPacketPayload {
    public static final Type<GiveRequestPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("panoptic", "give_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, GiveRequestPacket> STREAM_CODEC =
            StreamCodec.ofMember(GiveRequestPacket::encode, GiveRequestPacket::decode);

    public final List<ItemStack> items;

    public GiveRequestPacket(List<ItemStack> items) {
        this.items = items;
    }

    @Override
    public Type<GiveRequestPacket> type() {
        return TYPE;
    }

    public static void encode(GiveRequestPacket msg, RegistryFriendlyByteBuf buf) {
        int n = Math.min(msg.items.size(), 27);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, msg.items.get(i));
        }
    }

    public static GiveRequestPacket decode(RegistryFriendlyByteBuf buf) {
        int n = Math.min(buf.readVarInt(), 27);
        List<ItemStack> items = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            items.add(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf));
        }
        return new GiveRequestPacket(items);
    }

    public static void handle(GiveRequestPacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp)
                    || !PermsStore.resolve(sp, "panoptic.screens.give")) {
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
    }
}