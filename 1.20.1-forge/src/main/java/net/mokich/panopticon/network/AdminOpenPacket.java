package net.mokich.panopticon.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent.Context;
import net.mokich.panopticon.perms.PermsAdmin;

import java.util.function.Supplier;

public class AdminOpenPacket {
    public static void encode(AdminOpenPacket msg, FriendlyByteBuf buf) {}
    public static AdminOpenPacket decode(FriendlyByteBuf buf) {
        return new AdminOpenPacket();
    }

    public static void handle(AdminOpenPacket msg, Supplier<Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null && PermsAdmin.isAdmin(sender)) {
                PermsAdmin.sendState(sender);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}