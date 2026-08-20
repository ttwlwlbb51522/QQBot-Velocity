package cn.citprobe;

import cn.citprobe.core.*;
import cn.citprobe.velocity.*;
import com.google.gson.JsonObject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;

import jakarta.inject.Inject;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class QQBotPlugin {

    private final ProxyServer proxy;
    private final org.slf4j.Logger logger;
    private final Path dataDirectory;
    private final BotLogger botLogger;

    private Config config;
    private Lang lang;
    private BindingManager bindings;
    private VerificationManager verification;
    private VelocityStatusProvider statusProvider;
    private CommandHandler commandHandler;
    private BotWebSocketClient client;

    @Inject
    public QQBotPlugin(ProxyServer proxy, org.slf4j.Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
        this.botLogger = new BotLogger() {
            public void info(String m)  { logger.info(m); }
            public void warn(String m)  { logger.warn(m); }
            public void error(String m) { logger.error(m); }
        };
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        try {
            Files.createDirectories(dataDirectory);
        } catch (Exception e) {
            logger.error("创建数据目录失败: " + e.getMessage());
        }

        config = new Config();
        config.load(dataDirectory, logger);

        if (!config.enabled) {
            logger.info("插件已在配置中禁用");
            return;
        }

        ensureLangFiles();
        lang = new Lang(dataDirectory.resolve("lang"), config.language, botLogger);
        bindings = new BindingManager(dataDirectory, "qqbot-bindings.json", botLogger);
        verification = new VerificationManager();
        statusProvider = new VelocityStatusProvider(proxy, config.backendTimeoutSeconds * 1000L);
        commandHandler = new CommandHandler(statusProvider, verification, bindings, lang,
                config.commandPrefix, config.serverName);

        // 1. 注册 bridge 频道并监听后端 Paper 插件的上报
        proxy.getChannelRegistrar().register(BridgeListener.CHANNEL);
        proxy.getEventManager().register(this, new BridgeListener(statusProvider));

        // 2. 连接中转站（先创建 client，供命令与事件监听使用）
        String secret = SecretManager.load(dataDirectory, config.secretFile, botLogger);
        client = new BotWebSocketClient(config.wsUrl, secret, config.reconnectDelaySeconds, botLogger,
                new BotWebSocketClient.Listener() {
                    @Override public void onOpen() { logger.info("中转站连接已建立"); }
                    @Override public void onText(IncomingMessage msg) { handleIncoming(msg); }
                    @Override public void onAck(JsonObject data) { }
                    @Override public void onError(String error) { logger.error("中转站错误: " + error); }
                    @Override public void onClose() { logger.warn("中转站连接已断开"); }
                });
        client.start();

        // 3. 注册游戏内命令（代理命令，对所有后端生效）
        BindCommands bc = new BindCommands(verification, bindings, lang, client);
        proxy.getCommandManager().register(
                proxy.getCommandManager().metaBuilder("bind").plugin(this).build(),
                bc.bindCommand());
        proxy.getCommandManager().register(
                proxy.getCommandManager().metaBuilder("unbind").plugin(this).build(),
                bc.unbindCommand());

        // 4. 上线提醒事件
        proxy.getEventManager().register(this,
                new PlayerEventListener(bindings, lang, config.serverName, client));
    }

    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (client != null) client.stop();
    }

    private void ensureLangFiles() {
        Path langDir = dataDirectory.resolve("lang");
        for (String f : new String[]{"zh_cn.json", "en_us.json"}) {
            Path target = langDir.resolve(f);
            if (Files.exists(target)) continue;
            try (InputStream in = getClass().getResourceAsStream("/lang/" + f)) {
                if (in != null) {
                    Files.createDirectories(langDir);
                    Files.copy(in, target);
                }
            } catch (Exception e) {
                logger.warn("复制语言文件失败: " + f + " -> " + e.getMessage());
            }
        }
    }

    private void handleIncoming(IncomingMessage msg) {
        if (msg == null || msg.data == null) return;
        String openid = msg.data.openid;
        String text = msg.data.message;
        String groupOpenid = msg.data.groupOpenid;
        boolean isGroup = "group_message".equals(msg.type);

        proxy.getScheduler().buildTask(this, () -> {
            String reply = commandHandler.dispatch(openid, text);
            if (reply == null) return;
            if (isGroup) client.sendGroup(groupOpenid, reply);
            else client.sendC2c(openid, reply);
        }).schedule();
    }
}
