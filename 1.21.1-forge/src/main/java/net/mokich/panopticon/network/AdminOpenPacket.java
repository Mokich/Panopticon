package net.mokich.panopticon.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.mokich.panopticon.perms.PermsAdmin;


public class AdminOpenPacket {
    public static void encode(AdminOpenPacket msg, FriendlyByteBuf buf) {}
    public static AdminOpenPacket decode(FriendlyByteBuf buf) {
        return new AdminOpenPacket();
    }

    public static void handle(AdminOpenPacket msg, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            ServerPlayer sender = ctx.getSender();
            if (sender != null && PermsAdmin.isAdmin(sender)) {
                PermsAdmin.sendState(sender);
            }
        });
        ctx.setPacketHandled(true);
    }
}