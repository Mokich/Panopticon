package net.mokich.panopticon.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;


public class SeedPushPacket {
    public final boolean granted;
    public final long seed;

    public SeedPushPacket(boolean granted, long seed) {
        this.granted = granted;
        this.seed = seed;
    }

    public static void encode(SeedPushPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.granted);
        buf.writeLong(msg.seed);
    }

    public static SeedPushPacket decode(FriendlyByteBuf buf) {
        return new SeedPushPacket(buf.readBoolean(), buf.readLong());
    }

    public static void handle(SeedPushPacket msg, CustomPayloadEvent.Context ctx) {
        ctx.setPacketHandled(true);
    }
}