package cn.citprobe.velocity;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.messages.ChannelIdentifier;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class BridgeListener {

    public static final MinecraftChannelIdentifier CHANNEL =
            MinecraftChannelIdentifier.create("qqbot", "bridge");

    private final VelocityStatusProvider statusProvider;

    public BridgeListener(VelocityStatusProvider statusProvider) {
        this.statusProvider = statusProvider;
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        ChannelIdentifier id = event.getIdentifier();
        if (!CHANNEL.equals(id)) return;

        // 后端 Paper 插件上报的消息，标记为已处理，不再转发
        event.setResult(PluginMessageEvent.ForwardResult.handled());

        try {
            String text = new String(event.getData(), StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(text).getAsJsonObject();
            if (!"status".equals(obj.has("type") ? obj.get("type").getAsString() : "")) return;

            String server = obj.get("server").getAsString();
            double tps = obj.has("tps") ? obj.get("tps").getAsDouble() : -1.0;
            double mspt = obj.has("mspt") ? obj.get("mspt").getAsDouble() : -1.0;
            int players = obj.has("players") ? obj.get("players").getAsInt() : -1;
            int max = obj.has("max") ? obj.get("max").getAsInt() : -1;

            List<String> names = new ArrayList<>();
            if (obj.has("playerNames") && obj.get("playerNames").isJsonArray()) {
                for (JsonElement e : obj.getAsJsonArray("playerNames")) {
                    names.add(e.getAsString());
                }
            }

            // 6 个参数，与 VelocityStatusProvider.updateBackend 保持一致
            statusProvider.updateBackend(server, tps, mspt, players, max, names);
        } catch (Exception ignored) {
            // 忽略解析失败的消息
        }
    }
}
