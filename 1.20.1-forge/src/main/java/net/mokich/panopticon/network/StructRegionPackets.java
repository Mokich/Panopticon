package net.mokich.panopticon.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent.Context;
import net.minecraftforge.network.PacketDistributor;
import net.mokich.panopticon.Panopticon;
import net.mokich.panopticon.perms.PermsStore;
import net.mokich.panopticon.seed.RegionScan;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public final class StructRegionPackets {
    private StructRegionPackets() {}

    public static class Request {
        public final String dim;
        public final int rx;
        public final int rz;

        public Request(String dim, int rx, int rz) {
            this.dim = dim;
            this.rx = rx;
            this.rz = rz;
        }

        public static void encode(Request msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.dim, 256);
            buf.writeVarInt(msg.rx);
            buf.writeVarInt(msg.rz);
        }

        public static Request decode(FriendlyByteBuf buf) {
            return new Request(buf.readUtf(256), buf.readVarInt(), buf.readVarInt());
        }

        public static void handle(Request msg, Supplier<Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer sp = ctx.get().getSender();
                if (sp == null || !PermsStore.resolve(sp, "panoptic.seed.structures")) {
                    return;
                }
                ServerLevel level = RegionScan.levelFor(sp.server, msg.dim);
                if (level == null) {
                    return;
                }
                UUID uid = sp.getUUID();
                RegionScan.compute(level, msg.rx, msg.rz, found -> {
                    ServerPlayer target = sp.server.getPlayerList().getPlayer(uid);
                    if (target != null && Panopticon.CHANNEL != null) {
                        Panopticon.CHANNEL.send(PacketDistributor.PLAYER.with(() -> target),
                                new Result(msg.dim, msg.rx, msg.rz, found));
                    }
                });
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class Result {
        public final String dim;
        public final int rx;
        public final int rz;
        public final List<RegionScan.Found> found;

        public Result(String dim, int rx, int rz, List<RegionScan.Found> found) {
            this.dim = dim;
            this.rx = rx;
            this.rz = rz;
            this.found = found;
        }

        public static void encode(Result msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.dim, 256);
            buf.writeVarInt(msg.rx);
            buf.writeVarInt(msg.rz);
            int n = Math.min(msg.found.size(), 4096);
            buf.writeVarInt(n);
            for (int i = 0; i < n; i++) {
                RegionScan.Found f = msg.found.get(i);
                buf.writeUtf(f.id(), 256);
                buf.writeVarInt(f.x());
                buf.writeVarInt(f.z());
            }
        }

        public static Result decode(FriendlyByteBuf buf) {
            String dim = buf.readUtf(256);
            int rx = buf.readVarInt();
            int rz = buf.readVarInt();
            int n = Math.min(buf.readVarInt(), 4096);
            List<RegionScan.Found> found = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                found.add(new RegionScan.Found(buf.readUtf(256), buf.readVarInt(), buf.readVarInt()));
            }
            return new Result(dim, rx, rz, found);
        }

        public static void handle(Result msg, Supplier<Context> ctx) {
            ctx.get().setPacketHandled(true);
        }
    }
}