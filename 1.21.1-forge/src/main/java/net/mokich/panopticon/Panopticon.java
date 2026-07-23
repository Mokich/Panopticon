package net.mokich.panopticon;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.SimpleChannel;
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
        CHANNEL = ChannelBuilder.named(ResourceLocation.fromNamespaceAndPath("panoptic", "perms"))
                .networkProtocolVersion(1).optional().simpleChannel();
        CHANNEL.messageBuilder(PermsSyncPacket.class, 0).encoder(PermsSyncPacket::encode).decoder(PermsSyncPacket::decode).consumerNetworkThread(PermsSyncPacket::handle).add();
        CHANNEL.messageBuilder(AdminOpenPacket.class, 1).encoder(AdminOpenPacket::encode).decoder(AdminOpenPacket::decode).consumerNetworkThread(AdminOpenPacket::handle).add();
        CHANNEL.messageBuilder(AdminStatePacket.class, 2).encoder(AdminStatePacket::encode).decoder(AdminStatePacket::decode).consumerNetworkThread(AdminStatePacket::handle).add();
        CHANNEL.messageBuilder(AdminEditPacket.class, 3).encoder(AdminEditPacket::encode).decoder(AdminEditPacket::decode).consumerNetworkThread(AdminEditPacket::handle).add();
        CHANNEL.messageBuilder(SeedPushPacket.class, 4).encoder(SeedPushPacket::encode).decoder(SeedPushPacket::decode).consumerNetworkThread(SeedPushPacket::handle).add();
        CHANNEL.messageBuilder(SpawnVillagerPacket.class, 5).encoder(SpawnVillagerPacket::encode).decoder(SpawnVillagerPacket::decode).consumerNetworkThread(SpawnVillagerPacket::handle).add();
        CHANNEL.messageBuilder(GiveRequestPacket.class, 6).encoder(GiveRequestPacket::encode).decoder(GiveRequestPacket::decode).consumerNetworkThread(GiveRequestPacket::handle).add();
        CHANNEL.messageBuilder(StructRegionPackets.Request.class, 9).encoder(StructRegionPackets.Request::encode).decoder(StructRegionPackets.Request::decode).consumerNetworkThread(StructRegionPackets.Request::handle).add();
        CHANNEL.messageBuilder(StructRegionPackets.Result.class, 10).encoder(StructRegionPackets.Result::encode).decoder(StructRegionPackets.Result::decode).consumerNetworkThread(StructRegionPackets.Result::handle).add();
        CHANNEL.messageBuilder(BiomeTilePackets.Request.class, 11).encoder(BiomeTilePackets.Request::encode).decoder(BiomeTilePackets.Request::decode).consumerNetworkThread(BiomeTilePackets.Request::handle).add();
        CHANNEL.messageBuilder(BiomeTilePackets.Result.class, 12).encoder(BiomeTilePackets.Result::encode).decoder(BiomeTilePackets.Result::decode).consumerNetworkThread(BiomeTilePackets.Result::handle).add();
        CHANNEL.build();
        MAIN_CHANNEL = ChannelBuilder.named(ResourceLocation.fromNamespaceAndPath("panoptic", "main"))
                .networkProtocolVersion(1).optional().simpleChannel();
        MAIN_CHANNEL.messageBuilder(OraclePackets.Check.class, 0).encoder(OraclePackets.Check::encode).decoder(OraclePackets.Check::decode).consumerNetworkThread(OraclePackets.Check::handle).add();
        MAIN_CHANNEL.messageBuilder(OraclePackets.CheckResult.class, 1).encoder(OraclePackets.CheckResult::encode).decoder(OraclePackets.CheckResult::decode).consumerNetworkThread(OraclePackets.CheckResult::handle).add();
        MAIN_CHANNEL.messageBuilder(OraclePackets.All.class, 2).encoder(OraclePackets.All::encode).decoder(OraclePackets.All::decode).consumerNetworkThread(OraclePackets.All::handle).add();
        MAIN_CHANNEL.messageBuilder(OraclePackets.AllResult.class, 3).encoder(OraclePackets.AllResult::encode).decoder(OraclePackets.AllResult::decode).consumerNetworkThread(OraclePackets.AllResult::handle).add();
        MAIN_CHANNEL.build();
    }
}
