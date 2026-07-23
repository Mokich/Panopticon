package net.mokich.panopticon.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.mokich.panopticon.perms.PermsAdmin;

public class AdminOpenPacket {
    public static final ResourceLocation CHANNEL = new ResourceLocation("panoptic", "admin_open");

    public static void encode(AdminOpenPacket msg, FriendlyByteBuf buf) {}

    public static AdminOpenPacket decode(FriendlyByteBuf buf) {
        return new AdminOpenPacket();
    }

    public static void handle(AdminOpenPacket msg, ServerPlayer sender) {
        if (PermsAdmin.isAdmin(sender)) {
            PermsAdmin.sendState(sender);
        }
    }
}