package cn.citprobe.velocity;

import cn.citprobe.core.ServerStatus;
import cn.citprobe.core.StatusProvider;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class VelocityStatusProvider implements StatusProvider {

    private final ProxyServer proxy;
    private final long timeoutMillis;

    // 已接入 QQBot 的后端：key = 名称小写，value = 桥接上报数据
    private final Map<String, BackendInfo> backends = new ConcurrentHashMap<>();

    private static final class BackendInfo {
        final String serverName;                 // QQBot 配置里的 serverName
        volatile double tps = -1.0;
        volatile double mspt = -1.0;
        volatile int players = -1;
        volatile int max = -1;
        volatile List<String> playerNames = List.of();
        volatile long lastUpdate = System.currentTimeMillis();

        BackendInfo(String serverName) {
            this.serverName = serverName;
        }
    }

    public VelocityStatusProvider(ProxyServer proxy, long timeoutMillis) {
        this.proxy = proxy;
        this.timeoutMillis = timeoutMillis;
    }

    public void updateBackend(String server, double tps, double mspt,
                              int players, int max, List<String> playerNames) {
        BackendInfo b = backends.computeIfAbsent(server.toLowerCase(Locale.ROOT),
                k -> new BackendInfo(server));
        b.tps = tps;
        b.mspt = mspt;
        b.players = players;
        b.max = max;
        b.playerNames = playerNames == null ? List.of() : playerNames;
        b.lastUpdate = System.currentTimeMillis();
    }

    private boolean isAlive(BackendInfo b) {
        return System.currentTimeMillis() - b.lastUpdate <= timeoutMillis;
    }

    @Override
    public List<ServerStatus> getAllServers() {
        List<ServerStatus> result = new ArrayList<>();
        for (BackendInfo b : backends.values()) {
            if (!isAlive(b)) continue;          // 超时未上报的不显示
            result.add(buildStatus(b));
        }
        result.sort(Comparator.comparing(o -> o.serverName));
        return result;
    }

    @Override
    public ServerStatus getServer(String name) {
        if (name == null) return null;
        for (ServerStatus s : getAllServers()) {
            if (s.serverName.equalsIgnoreCase(name)) return s;
        }
        return null;
    }

    @Override
    public ServerStatus getPlayerServer(String playerName) {
        // 在已接入的后端里找包含该玩家的那个
        for (BackendInfo b : backends.values()) {
            if (!isAlive(b)) continue;
            for (String n : b.playerNames) {
                if (n.equalsIgnoreCase(playerName)) return buildStatus(b);
            }
        }
        return null;
    }

    @Override
    public Integer getPlayerPing(String playerName) {
        Optional<Player> op = proxy.getPlayer(playerName);
        return op.map(p -> (int) p.getPing()).orElse(null);
    }

    @Override
    public boolean hasTpsData() {
        for (BackendInfo b : backends.values()) {
            if (isAlive(b)) return true;
        }
        return false;
    }

    private ServerStatus buildStatus(BackendInfo b) {
        ServerStatus s = new ServerStatus(b.serverName);
        s.tps = b.tps;
        s.mspt = b.mspt;
        s.maxPlayers = b.max;
        s.players = b.players >= 0 ? b.players : b.playerNames.size();
        s.online = true;
        s.lastUpdate = b.lastUpdate;

        for (String name : b.playerNames) {
            s.playerNames.add(name);
            proxy.getPlayer(name).ifPresent(p -> s.playerPings.put(name, (int) p.getPing()));
        }
        return s;
    }
}
