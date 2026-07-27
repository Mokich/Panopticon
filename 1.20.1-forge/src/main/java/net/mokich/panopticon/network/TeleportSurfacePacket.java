package net.mokich.panopticon.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.network.NetworkEvent.Context;
import net.mokich.panopticon.perms.PermsStore;

import java.util.function.Supplier;

public class TeleportSurfacePacket {
    public final ResourceLocation dim;
    public final int x;
    public final int z;

    public TeleportSurfacePacket(ResourceLocation dim, int x, int z) {
        this.dim = dim;
        this.x = x;
        this.z = z;
    }

    public static void encode(TeleportSurfacePacket msg, FriendlyByteBuf buf) {
        buf.writeResourceLocation(msg.dim);
        buf.writeVarInt(msg.x);
        buf.writeVarInt(msg.z);
    }

    public static TeleportSurfacePacket decode(FriendlyByteBuf buf) {
        return new TeleportSurfacePacket(buf.readResourceLocation(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(TeleportSurfacePacket msg, Supplier<Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sp = ctx.get().getSender();
            if (sp == null || !PermsStore.resolve(sp, "panoptic.seed.teleport")) {
                return;
            }
            MinecraftServer server = sp.getServer();
            if (server == null) {
                return;
            }
            ServerLevel sl = server.getLevel(ResourceKey.create(Registries.DIMENSION, msg.dim));
            if (sl == null) {
                return;
            }
            sl.getChunk(msg.x >> 4, msg.z >> 4);
            double y = safeSurfaceY(sl, msg.x, msg.z);
            sp.teleportTo(sl, msg.x + 0.5, y, msg.z + 0.5, sp.getYRot(), sp.getXRot());
        });
        ctx.get().setPacketHandled(true);
    }

    private static double safeSurfaceY(ServerLevel sl, int wx, int wz) {
        int minY = sl.getMinBuildHeight();
        int maxY = sl.getMaxBuildHeight();
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