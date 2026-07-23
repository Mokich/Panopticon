package net.mokich.panopticon.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.mokich.panopticon.perms.PermsAdmin;

public class AdminEditPacket {
    public static final ResourceLocation CHANNEL = new ResourceLocation("panoptic", "admin_edit");

    public final byte op;
    public final String a;
    public final String b;

    public AdminEditPacket(byte op, String a, String b) {
        this.op = op;
        this.a = a;
        this.b = b;
    }

    public static void encode(AdminEditPacket msg, FriendlyByteBuf buf) {
        buf.writeByte(msg.op);
        buf.writeUtf(msg.a, 32000);
        buf.writeUtf(msg.b, 64);
    }

    public static AdminEditPacket decode(FriendlyByteBuf buf) {
        byte op = buf.readByte();
        String a = buf.readUtf(32000);
        String b = buf.readUtf(64);
        return new AdminEditPacket(op, a, b);
    }

    public static void handle(AdminEditPacket msg, ServerPlayer sender) {
        if (PermsAdmin.isAdmin(sender)) {
            PermsAdmin.applyEdit(sender, msg.op, msg.a, msg.b);
        }
    }
}