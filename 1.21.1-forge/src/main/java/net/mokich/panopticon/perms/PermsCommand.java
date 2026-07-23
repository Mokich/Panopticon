package net.mokich.panopticon.perms;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;

public final class PermsCommand {
    private static final SuggestionProvider<CommandSourceStack> NODE_SUGGEST =
            (ctx, b) -> SharedSuggestionProvider.suggest(PermsStore.NODES, b);
    private static final SuggestionProvider<CommandSourceStack> PLAYER_NODE_SUGGEST =
            (ctx, b) -> SharedSuggestionProvider.suggest(PermsStore.PLAYER_NODES, b);
    private static final SuggestionProvider<CommandSourceStack> GROUP_SUGGEST =
            (ctx, b) -> SharedSuggestionProvider.suggest(PermsStore.groupNames(), b);

    private PermsCommand() {
    }

    private static boolean canUse(CommandSourceStack src) {
        if (src.hasPermission(3)) {
            return true;
        }
        return src.getEntity() instanceof ServerPlayer sp && PermsAdmin.isAdmin(sp);
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("panopticon").requires(PermsCommand::canUse)
                .then(Commands.literal("reload").executes(ctx -> {
                    PermsStore.load();
                    PermsEvents.resyncAll(ctx.getSource().getServer());
                    reply(ctx.getSource(), "perms.json reloaded");
                    return 1;
                }))
                .then(Commands.literal("group")
                        .then(Commands.literal("create")
                                .then(Commands.argument("name", StringArgumentType.word()).executes(ctx -> {
                                    String name = StringArgumentType.getString(ctx, "name");
                                    boolean ok = PermsStore.createGroup(name);
                                    reply(ctx.getSource(), ok ? "group created: " + name : "group already exists");
                                    return ok ? 1 : 0;
                                })))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("name", StringArgumentType.word()).suggests(GROUP_SUGGEST)
                                        .executes(ctx -> {
                                            String name = StringArgumentType.getString(ctx, "name");
                                            boolean ok = PermsStore.removeGroup(name);
                                            PermsEvents.resyncAll(ctx.getSource().getServer());
                                            reply(ctx.getSource(), ok ? "group removed: " + name : "cannot remove");
                                            return ok ? 1 : 0;
                                        })))
                        .then(Commands.literal("allow").then(groupNodeArg(true)))
                        .then(Commands.literal("revoke").then(groupNodeArg(false))))
                .then(Commands.literal("player")
                        .then(Commands.literal("group")
                                .then(Commands.argument("targets", GameProfileArgument.gameProfile())
                                        .then(Commands.argument("name", StringArgumentType.word()).suggests(GROUP_SUGGEST)
                                                .executes(ctx -> {
                                                    String name = StringArgumentType.getString(ctx, "name");
                                                    if (!PermsStore.groupNames().contains(name)) {
                                                        reply(ctx.getSource(), "no such group");
                                                        return 0;
                                                    }
                                                    Collection<GameProfile> targets =
                                                            GameProfileArgument.getGameProfiles(ctx, "targets");
                                                    for (GameProfile gp : targets) {
                                                        PermsStore.setPlayerGroup(gp.getId(), name);
                                                    }
                                                    PermsEvents.resyncAll(ctx.getSource().getServer());
                                                    reply(ctx.getSource(), "group set for " + targets.size());
                                                    return targets.size();
                                                }))))
                        .then(Commands.literal("allow").then(playerNodeArg(1)))
                        .then(Commands.literal("deny").then(playerNodeArg(-1)))
                        .then(Commands.literal("unset").then(playerNodeArg(0))))
                .then(Commands.literal("info")
                        .then(Commands.argument("target", GameProfileArgument.gameProfile()).executes(ctx -> {
                            Collection<GameProfile> targets = GameProfileArgument.getGameProfiles(ctx, "target");
                            for (GameProfile gp : targets) {
                                ServerPlayer sp = ctx.getSource().getServer().getPlayerList().getPlayer(gp.getId());
                                if (sp == null) {
                                    reply(ctx.getSource(), gp.getName() + ": offline, resolved perms unavailable");
                                } else {
                                    reply(ctx.getSource(), gp.getName() + ":\n" + PermsStore.describe(sp)
                                            + PermsStore.ADMIN_NODE + " = " + PermsAdmin.isAdmin(sp));
                                }
                            }
                            return 1;
                        }))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> groupNodeArg(boolean add) {
        return Commands.argument("name", StringArgumentType.word()).suggests(GROUP_SUGGEST)
                .then(Commands.argument("node", StringArgumentType.word()).suggests(NODE_SUGGEST)
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "name");
                            String node = StringArgumentType.getString(ctx, "node");
                            boolean ok = PermsStore.setGroupNode(name, node, add);
                            PermsEvents.resyncAll(ctx.getSource().getServer());
                            reply(ctx.getSource(), ok ? "updated " + name : "no change");
                            return ok ? 1 : 0;
                        }));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, ?> playerNodeArg(int mode) {
        return Commands.argument("targets", GameProfileArgument.gameProfile())
                .then(Commands.argument("node", StringArgumentType.word()).suggests(PLAYER_NODE_SUGGEST)
                        .executes(ctx -> {
                            String node = StringArgumentType.getString(ctx, "node");
                            Collection<GameProfile> targets = GameProfileArgument.getGameProfiles(ctx, "targets");
                            int n = 0;
                            for (GameProfile gp : targets) {
                                if (PermsStore.setPlayerNode(gp.getId(), node, mode)) {
                                    n++;
                                }
                            }
                            PermsEvents.resyncAll(ctx.getSource().getServer());
                            reply(ctx.getSource(), "updated " + n);
                            return n;
                        }));
    }

    private static void reply(CommandSourceStack src, String msg) {
        src.sendSuccess(() -> Component.literal(msg), false);
    }
}