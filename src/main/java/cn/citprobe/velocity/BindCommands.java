package cn.citprobe.velocity;

import cn.citprobe.core.BindingManager;
import cn.citprobe.core.BotWebSocketClient;
import cn.citprobe.core.Lang;
import cn.citprobe.core.VerificationManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;

public class BindCommands {

    private final VerificationManager verification;
    private final BindingManager bindings;
    private final Lang lang;
    private final BotWebSocketClient client;

    public BindCommands(VerificationManager verification, BindingManager bindings,
                        Lang lang, BotWebSocketClient client) {
        this.verification = verification;
        this.bindings = bindings;
        this.lang = lang;
        this.client = client;
    }

    public BrigadierCommand bindCommand() {
        LiteralCommandNode<CommandSource> node =
                LiteralArgumentBuilder.<CommandSource>literal("bind")
                        .then(LiteralArgumentBuilder.<CommandSource>literal("accept")
                                .then(RequiredArgumentBuilder.<CommandSource, String>argument(
                                                "code", StringArgumentType.word())
                                        .executes(ctx -> {
                                            String code = ctx.getArgument("code", String.class);
                                            return accept(ctx.getSource(), code);
                                        })))
                        .executes(ctx -> {
                            ctx.getSource().sendMessage(Component.text(lang.get("bind.accept.usage")));
                            return 1;
                        })
                        .build();
        return new BrigadierCommand(node);
    }

    public BrigadierCommand unbindCommand() {
        LiteralCommandNode<CommandSource> node =
                LiteralArgumentBuilder.<CommandSource>literal("unbind")
                        .executes(ctx -> {
                            CommandSource src = ctx.getSource();
                            if (!(src instanceof Player player)) {
                                src.sendMessage(Component.text("仅玩家可执行此命令"));
                                return 1;
                            }
                            String openid = bindings.getOpenid(player.getUsername());
                            if (openid == null) {
                                player.sendMessage(Component.text(lang.get("unbind.game.not_bound")));
                                return 1;
                            }
                            bindings.unbindByGameId(player.getUsername());
                            player.sendMessage(Component.text(lang.get("unbind.game.success")));
                            client.sendC2c(openid, lang.get("unbind.notify", player.getUsername()));
                            return 1;
                        })
                        .build();
        return new BrigadierCommand(node);
    }

    private int accept(CommandSource src, String code) {
        if (!(src instanceof Player player)) {
            src.sendMessage(Component.text("仅玩家可执行此命令"));
            return 1;
        }
        VerificationManager.Entry e = verification.peek(code);
        if (e == null) {
            player.sendMessage(Component.text(lang.get(
                    verification.hasCode(code) ? "bind.accept.expired" : "bind.accept.notfound")));
            return 1;
        }
        if (!e.gameId.equalsIgnoreCase(player.getUsername())) {
            player.sendMessage(Component.text(lang.get("bind.accept.mismatch")));
            return 1;
        }
        verification.consume(code);
        String existing = bindings.getOpenid(e.gameId);
        if (existing != null && !existing.equals(e.openid)) {
            player.sendMessage(Component.text(lang.get("bind.accept.bound_by_other")));
            return 1;
        }
        bindings.bind(e.openid, e.gameId);
        player.sendMessage(Component.text(lang.get("bind.accept.success", e.gameId)));
        client.sendC2c(e.openid, lang.get("bind.notify", e.gameId));
        return 1;
    }
}
