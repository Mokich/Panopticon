package net.mokich.panopticon.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.mokich.panopticon.perms.PermsStore;
import net.mokich.panopticon.seed.RegionScan;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class StructRegionPackets {
    private StructRegionPackets() {}

    public static class Request implements CustomPacketPayload {
        public static final Type<Request> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath("panoptic", "struct_region_request"));
        public static final StreamCodec<FriendlyByteBuf, Request> STREAM_CODEC =
                StreamCodec.ofMember(Request::encode, Request::decode);

        public final String dim;
        public final int rx;
        public final int rz;

        public Request(String dim, int rx, int rz) {
            this.dim = dim;
            this.rx = rx;
            this.rz = rz;
        }

        @Override
        public Type<Request> type() {
            return TYPE;
        }

        public static void encode(Request msg, FriendlyByteBuf buf) {
            buf.writeUtf(msg.dim, 256);
            buf.writeVarInt(msg.rx);
            buf.writeVarInt(msg.rz);
        }

        public static Request decode(FriendlyByteBuf buf) {
            return new Request(buf.readUtf(256), buf.readVarInt(), buf.readVarInt());
        }

        public static void handle(Request msg, ServerPlayNetworking.Context ctx) {
            ctx.server().execute(() -> {
                if (!(ctx.player() instanceof ServerPlayer sp)
                        || !PermsStore.resolve(sp, "panoptic.seed.structures")) {
                    return;
                }
                ServerLevel level = RegionScan.levelFor(sp.server, msg.dim);
                if (level == null) {
                    return;
                }
                UUID uid = sp.getUUID();
                RegionScan.compute(level, msg.rx, msg.rz, found -> {
                    ServerPlayer target = sp.server.getPlayerList().getPlayer(uid);
                    if (target != null) {
                        ServerPlayNetworking.send(target, new Result(msg.dim, msg.rx, msg.rz, found));
                    }
                });
            });
        }
    }

    public static class Result implements CustomPacketPayload {
        public static final Type<Result> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath("panoptic", "struct_region_result"));
        public static final StreamCodec<FriendlyByteBuf, Result> STREAM_CODEC =
                StreamCodec.ofMember(Result::encode, Result::decode);

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

        @Override
        public Type<Result> type() {
            return TYPE;
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

        public static void handle(Result msg, ServerPlayNetworking.Context ctx) {}
    }
}