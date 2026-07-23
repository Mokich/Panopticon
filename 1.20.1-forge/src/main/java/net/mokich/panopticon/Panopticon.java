package net.mokich.panopticon;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
import net.mokich.panopticon.network.*;

@Mod(Panopticon.MODID)
public class Panopticon {
    public static final String MODID = "panopticon";
    public static SimpleChannel CHANNEL;
    public static SimpleChannel MAIN_CHANNEL;

    public Panopticon() {
        if (!FMLEnvironment.dist.isDedicatedServer()) {
            return;
        }
        if (ModList.get().isLoaded("panoptic")) {
            throw new IllegalStateException(
                    "Panopticon replaces Panoptic on the server. Remove Panoptic from the server mods folder.");
        }
        CHANNEL = NetworkRegistry.newSimpleChannel(ResourceLocation.fromNamespaceAndPath("panoptic", "perms"), () -> "1", s -> true, s -> true);
        CHANNEL.registerMessage(0, PermsSyncPacket.class, PermsSyncPacket::encode, PermsSyncPacket::decode, PermsSyncPacket::handle);
        CHANNEL.registerMessage(1, AdminOpenPacket.class, AdminOpenPacket::encode, AdminOpenPacket::decode, AdminOpenPacket::handle);
        CHANNEL.registerMessage(2, AdminStatePacket.class, AdminStatePacket::encode, AdminStatePacket::decode, AdminStatePacket::handle);
        CHANNEL.registerMessage(3, AdminEditPacket.class, AdminEditPacket::encode, AdminEditPacket::decode, AdminEditPacket::handle);
        CHANNEL.registerMessage(4, SeedPushPacket.class, SeedPushPacket::encode, SeedPushPacket::decode, SeedPushPacket::handle);
        CHANNEL.registerMessage(5, SpawnVillagerPacket.class, SpawnVillagerPacket::encode, SpawnVillagerPacket::decode, SpawnVillagerPacket::handle);
        CHANNEL.registerMessage(6, GiveRequestPacket.class, GiveRequestPacket::encode, GiveRequestPacket::decode, GiveRequestPacket::handle);
        CHANNEL.registerMessage(9, StructRegionPackets.Request.class, StructRegionPackets.Request::encode, StructRegionPackets.Request::decode, StructRegionPackets.Request::handle);
        CHANNEL.registerMessage(10, StructRegionPackets.Result.class, StructRegionPackets.Result::encode, StructRegionPackets.Result::decode, StructRegionPackets.Result::handle);
        CHANNEL.registerMessage(11, BiomeTilePackets.Request.class, BiomeTilePackets.Request::encode, BiomeTilePackets.Request::decode, BiomeTilePackets.Request::handle);
        CHANNEL.registerMessage(12, BiomeTilePackets.Result.class, BiomeTilePackets.Result::encode, BiomeTilePackets.Result::decode, BiomeTilePackets.Result::handle);
        MAIN_CHANNEL = NetworkRegistry.newSimpleChannel(ResourceLocation.fromNamespaceAndPath("panoptic", "main"), () -> "1", s -> true, s -> true);
        MAIN_CHANNEL.registerMessage(0, OraclePackets.Check.class, OraclePackets.Check::encode, OraclePackets.Check::decode, OraclePackets.Check::handle);
        MAIN_CHANNEL.registerMessage(1, OraclePackets.CheckResult.class, OraclePackets.CheckResult::encode, OraclePackets.CheckResult::decode, OraclePackets.CheckResult::handle);
        MAIN_CHANNEL.registerMessage(2, OraclePackets.All.class, OraclePackets.All::encode, OraclePackets.All::decode, OraclePackets.All::handle);
        MAIN_CHANNEL.registerMessage(3, OraclePackets.AllResult.class, OraclePackets.AllResult::encode, OraclePackets.AllResult::decode, OraclePackets.AllResult::handle);
    }
}