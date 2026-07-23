package net.mokich.panopticon.network;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.PacketDistributor;
import net.mokich.panopticon.Panopticon;
import net.mokich.panopticon.perms.PermsStore;

import java.util.ArrayList;
import java.util.List;

public final class OraclePackets {
    private OraclePackets() {}

    public static class Check {
        public static void encode(Check msg, FriendlyByteBuf buf) {}

        public static Check decode(FriendlyByteBuf buf) {
            return new Check();
        }

        public static void handle(Check msg, CustomPayloadEvent.Context ctx) {
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player == null || !PermsStore.resolve(player, "panoptic.seed.structures")) {
                    return;
                }
                ServerLevel level = player.serverLevel();
                BlockPos pos = player.blockPosition();
                StructureManager structureManager = level.structureManager();
                RegistryAccess access = level.registryAccess();
                Registry<Structure> registry = access.registryOrThrow(Registries.STRUCTURE);
                List<CheckResult.StructInfo> found = new ArrayList<>();
                for (Holder<Structure> holder : registry.holders().toList()) {
                    StructureStart start = structureManager.getStructureAt(pos, holder.value());
                    if (start == null || !start.isValid() || !start.getBoundingBox().isInside(pos)) {
                        continue;
                    }
                    holder.unwrapKey().ifPresent(key -> {
                        BoundingBox box = start.getBoundingBox();
                        CheckResult.StructInfo info = new CheckResult.StructInfo();
                        info.id = key.location().toString();
                        info.minX = box.minX();
                        info.minY = box.minY();
                        info.minZ = box.minZ();
                        info.maxX = box.maxX();
                        info.maxY = box.maxY();
                        info.maxZ = box.maxZ();
                        info.pieces = start.getPieces().size();
                        holder.tags().forEach(tag -> info.tags.add("#" + tag.location()));
                        found.add(info);
                    });
                }
                if (!found.isEmpty()) {
                    Panopticon.MAIN_CHANNEL.send(new CheckResult(found), PacketDistributor.PLAYER.with(player));
                }
            });
            ctx.setPacketHandled(true);
        }
    }

    public static class CheckResult {
        public final List<StructInfo> structures;

        public CheckResult(List<StructInfo> structures) {
            this.structures = structures;
        }

        public static void encode(CheckResult msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.structures.size());
            for (StructInfo s : msg.structures) {
                buf.writeUtf(s.id);
                buf.writeInt(s.minX);
                buf.writeInt(s.minY);
                buf.writeInt(s.minZ);
                buf.writeInt(s.maxX);
                buf.writeInt(s.maxY);
                buf.writeInt(s.maxZ);
                buf.writeVarInt(s.pieces);
                buf.writeVarInt(s.tags.size());
                for (String tag : s.tags) {
                    buf.writeUtf(tag);
                }
            }
        }

        public static CheckResult decode(FriendlyByteBuf buf) {
            int count = buf.readVarInt();
            List<StructInfo> list = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                StructInfo s = new StructInfo();
                s.id = buf.readUtf();
                s.minX = buf.readInt();
                s.minY = buf.readInt();
                s.minZ = buf.readInt();
                s.maxX = buf.readInt();
                s.maxY = buf.readInt();
                s.maxZ = buf.readInt();
                s.pieces = buf.readVarInt();
                int tagCount = buf.readVarInt();
                for (int t = 0; t < tagCount; t++) {
                    s.tags.add(buf.readUtf());
                }
                list.add(s);
            }
            return new CheckResult(list);
        }

        public static void handle(CheckResult msg, CustomPayloadEvent.Context ctx) {
            ctx.setPacketHandled(true);
        }

        public static final class StructInfo {
            public String id = "";
            public int minX, minY, minZ, maxX, maxY, maxZ;
            public int pieces;
            public List<String> tags = new ArrayList<>();
        }
    }

    public static class All {
        public static void encode(All msg, FriendlyByteBuf buf) {
        }

        public static All decode(FriendlyByteBuf buf) {
            return new All();
        }

        public static void handle(All msg, CustomPayloadEvent.Context ctx) {
            ctx.enqueueWork(() -> {
                ServerPlayer player = ctx.getSender();
                if (player == null || !PermsStore.resolve(player, "panoptic.seed.structures")) {
                    return;
                }
                RegistryAccess access = player.serverLevel().registryAccess();
                Registry<Structure> registry = access.registryOrThrow(Registries.STRUCTURE);
                List<AllResult.Info> list = new ArrayList<>();
                for (Holder.Reference<Structure> holder : registry.holders().toList()) {
                    holder.unwrapKey().ifPresent(key -> {
                        AllResult.Info info = new AllResult.Info();
                        info.id = key.location().toString();
                        holder.tags().forEach(tag -> info.tags.add("#" + tag.location()));
                        list.add(info);
                    });
                }
                if (!list.isEmpty()) {
                    Panopticon.MAIN_CHANNEL.send(new AllResult(list), PacketDistributor.PLAYER.with(player));
                }
            });
            ctx.setPacketHandled(true);
        }
    }

    public static class AllResult {
        public final List<Info> structures;

        public AllResult(List<Info> structures) {
            this.structures = structures;
        }

        public static void encode(AllResult msg, FriendlyByteBuf buf) {
            buf.writeVarInt(msg.structures.size());
            for (Info s : msg.structures) {
                buf.writeUtf(s.id);
                buf.writeVarInt(s.tags.size());
                for (String tag : s.tags) {
                    buf.writeUtf(tag);
                }
            }
        }

        public static AllResult decode(FriendlyByteBuf buf) {
            int count = buf.readVarInt();
            List<Info> list = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                Info s = new Info();
                s.id = buf.readUtf();
                int tagCount = buf.readVarInt();
                for (int t = 0; t < tagCount; t++) {
                    s.tags.add(buf.readUtf());
                }
                list.add(s);
            }
            return new AllResult(list);
        }

        public static void handle(AllResult msg, CustomPayloadEvent.Context ctx) {
            ctx.setPacketHandled(true);
        }

        public static final class Info {
            public String id = "";
            public List<String> tags = new ArrayList<>();
        }
    }
}