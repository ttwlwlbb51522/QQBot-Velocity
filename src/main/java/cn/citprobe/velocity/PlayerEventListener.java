package cn.citprobe.velocity;

import cn.citprobe.core.BindingManager;
import cn.citprobe.core.BotWebSocketClient;
import cn.citprobe.core.Lang;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.server.RegisteredServer;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayerEventListener {

    private final BindingManager bindings;
    private final Lang lang;
    private final String proxyServerName;
    private final BotWebSocketClient client;
    private final Set<UUID> reminded = ConcurrentHashMap.newKeySet();

    public PlayerEventListener(BindingManager bindings, Lang lang,
                               String proxyServerName, BotWebSocketClient client) {
        this.bindings = bindings;
        this.lang = lang;
        this.proxyServerName = proxyServerName;
        this.client = client;
    }

    @Subscribe
    public void onPostConnect(ServerPostConnectEvent event) {
        Player p = event.getPlayer();
        // 每次会话只提醒一次
        if (!reminded.add(p.getUniqueId())) return;

        String openid = bindings.getOpenid(p.getUsername());
        if (openid == null) return;

        String serverName = proxyServerName;
        RegisteredServer rs = p.getCurrentServer()
                .map(sc -> sc.getServer())
                .orElse(null);
        if (rs != null) serverName = rs.getServerInfo().getName();

        client.sendC2c(openid, lang.get("reminder.bound_join", p.getUsername(), serverName));
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        reminded.remove(event.getPlayer().getUniqueId());
    }
}
