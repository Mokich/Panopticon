package net.mokich.panopticon.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.mokich.panopticon.perms.PermsStore;
import java.util.Set;

public class TeleportSurfacePacket implements CustomPacketPayload {
    public static final Type<TeleportSurfacePacket> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("panoptic", "teleport_surface"));
    public static final StreamCodec<FriendlyByteBuf, TeleportSurfacePacket> STREAM_CODEC =
            StreamCodec.ofMember(TeleportSurfacePacket::encode, TeleportSurfacePacket::decode);

    public final Identifier dim;
    public final int x;
    public final int z;

    public TeleportSurfacePacket(Identifier dim, int x, int z) {
        this.dim = dim;
        this.x = x;
        this.z = z;
    }

    @Override
    public Type<TeleportSurfacePacket> type() {
        return TYPE;
    }

    public static void encode(TeleportSurfacePacket msg, FriendlyByteBuf buf) {
        buf.writeIdentifier(msg.dim);
        buf.writeVarInt(msg.x);
        buf.writeVarInt(msg.z);
    }

    public static TeleportSurfacePacket decode(FriendlyByteBuf buf) {
        return new TeleportSurfacePacket(buf.readIdentifier(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(TeleportSurfacePacket msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer sp) || !PermsStore.resolve(sp, "panoptic.seed.teleport")) {
                return;
            }
            MinecraftServer server = sp.level().getServer();
            if (server == null) {
                return;
            }
            ServerLevel sl = server.getLevel(ResourceKey.create(Registries.DIMENSION, msg.dim));
            if (sl == null) {
                return;
            }
            sl.getChunk(msg.x >> 4, msg.z >> 4);
            double y = safeSurfaceY(sl, msg.x, msg.z);
            sp.teleportTo(sl, msg.x + 0.5, y, msg.z + 0.5, Set.of(), sp.getYRot(), sp.getXRot(), false);
        });
    }

    private static double safeSurfaceY(ServerLevel sl, int wx, int wz) {
        int minY = sl.getMinY();
        int maxY = sl.getMaxY();
        if (!sl.dimensionType().hasCeiling()) {
            int h = sl.getHeight(Heightmap.Types.MOTION_BLOCKING, wx, wz);
            return Mth.clamp(h, minY + 1, maxY - 1);
        }
        int start = Mth.clamp(sl.dimensionType().logicalHeight() - 2, minY + 1, maxY - 2);
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int y = start; y > minY; y--) {
            BlockState floor = sl.getBlockState(p.set(wx, y, wz));
            if (!floor.blocksMotion() || !floor.getFluidState().isEmpty()) {
                continue;
            }
            BlockState a1 = sl.getBlockState(p.set(wx, y + 1, wz));
            BlockState a2 = sl.getBlockState(p.set(wx, y + 2, wz));
            if (!a1.blocksMotion() && a1.getFluidState().isEmpty() && !a2.blocksMotion()) {
                return y + 1;
            }
        }
        return start + 1;
    }
}