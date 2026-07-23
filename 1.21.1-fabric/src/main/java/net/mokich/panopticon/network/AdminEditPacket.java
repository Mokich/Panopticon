package net.mokich.panopticon.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.mokich.panopticon.perms.PermsAdmin;

public class AdminEditPacket implements CustomPacketPayload {
    public static final Type<AdminEditPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("panoptic", "admin_edit"));
    public static final StreamCodec<FriendlyByteBuf, AdminEditPacket> STREAM_CODEC =
            StreamCodec.ofMember(AdminEditPacket::encode, AdminEditPacket::decode);

    public final byte op;
    public final String a;
    public final String b;

    public AdminEditPacket(byte op, String a, String b) {
        this.op = op;
        this.a = a;
        this.b = b;
    }

    @Override
    public Type<AdminEditPacket> type() {
        return TYPE;
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

    public static void handle(AdminEditPacket msg, ServerPlayNetworking.Context ctx) {
        ctx.server().execute(() -> {
            if (ctx.player() instanceof ServerPlayer sender && PermsAdmin.isAdmin(sender)) {
                PermsAdmin.applyEdit(sender, msg.op, msg.a, msg.b);
            }
        });
    }
}