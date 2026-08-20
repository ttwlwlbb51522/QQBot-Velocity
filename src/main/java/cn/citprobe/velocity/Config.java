package cn.citprobe.velocity;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
    public boolean enabled = true;
    public String wsUrl = "ws://127.0.0.1:18080";
    public int reconnectDelaySeconds = 5;
    public String commandPrefix = "/";
    public String language = "zh_cn";
    public String serverName = "我的服务器";
    public String secretFile = "forwarding.secret";
    public int backendTimeoutSeconds = 10; // 后端超过该秒数未上报则视为 TPS 失效

    public void load(Path dataDir, org.slf4j.Logger logger) {
        Path file = dataDir.resolve("qqbot.json");
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(dataDir);
                Files.writeString(file, gson.toJson(this), StandardCharsets.UTF_8);
                logger.info("已生成默认配置文件: " + file);
                return;
            }
            Config loaded = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), Config.class);
            if (loaded == null) return;
            enabled = loaded.enabled;
            wsUrl = loaded.wsUrl;
            reconnectDelaySeconds = loaded.reconnectDelaySeconds;
            commandPrefix = loaded.commandPrefix;
            language = loaded.language;
            serverName = loaded.serverName;
            secretFile = loaded.secretFile;
            backendTimeoutSeconds = loaded.backendTimeoutSeconds;
        } catch (IOException e) {
            logger.warn("配置加载失败，使用默认值: " + e.getMessage());
        }
    }
}
