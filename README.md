# QQBot-Velocity

> 📖 在线文档：[https://doc.citprobe.cn/QQBot-Velocity](https://doc.citprobe.cn/QQBot-Velocity)

基于 [Velocity](https://papermc.io/software/velocity) 的 QQ 群服互联机器人插件，负责连接中转站并聚合展示各后端（Paper）上报的 TPS / MSPT / 人数。

> 🔗 这是 **Velocity（代理）版**。后端请使用 [QQBot-Paper](https://github.com/ttwlwlbb51522/QQBot-Paper) 插件。

## 功能特性

- QQ 指令：`/list` `/tps` `/ping` `/bind` `/unbind` `/me` `/help`
- 游戏内代理命令：`/bind accept <验证码>` `/unbind`（对所有后端生效）
- 验证码绑定：8 位（去除 0/O/1/I/L），5 分钟过期，游戏内确认
- 绑定玩家上线私聊提醒（含所在后端服务器名）
- 聚合展示各后端状态：只显示已接入 QQBot 的后端，名称取自后端 `config.yml` 的 `serverName`
- 连接与鉴权由本插件负责，双语文案、配置外置、密钥鉴权
- 未识别的一级指令静默忽略，便于多项目共用一个中转站机器人

## 双端架构

|            | QQBot-Paper         | QQBot-Velocity（本仓库） |
|------------|---------------------|---------------------|
| 运行位置       | 后端服务器               | 代理（Proxy）           |
| 连接中转站      | 检测到 Velocity 后交由本插件 | 负责连接与鉴权             |
| TPS / MSPT | 本地计算并通过 bridge 上报   | 聚合展示                |
| 游戏内命令      | 独立部署时处理             | 代理命令                |

**关键规则**：后端 Paper 插件检测到 Velocity 后，连接与鉴权交由本插件负责，Paper 端忽略 `wsUrl`，仅保留 TPS bridge 上报。

## 中转站协议

- 连接地址：`ws://<host>:18080`
- 鉴权头：`X-Forwarding-Secret: <16位密钥>`
- 密钥文件：`forwarding.secret`

### 桥接频道（接收 Paper 上报）

```
频道: qqbot:bridge
后端 → 代理 (JSON):
```json
{"type":"status","server":"<serverName>","tps":20.0,"mspt":48.5,"players":12,"max":100,"playerNames":["Steve","Alex"]}
```

## 指令说明

状态类指令：`/xx` 默认输出全部后端，`/xx <服务器名>` 输出对应后端，找不到则提示。

| 指令                 | 说明    |
|--------------------|-------|
| `/list [服务器]`      | 在线人数  |
| `/tps [服务器]`       | 服务器性能 |
| `/ping [游戏ID/服务器]` | 延迟    |
| `/bind <游戏ID>`     | 发起绑定  |
| `/unbind`          | 解绑    |
| `/me`              | 查看绑定  |
| `/help`            | 帮助    |

游戏内：`/bind accept <验证码>`、`/unbind`。

## 配置（qqbot.json）

```json
{
  "enabled": true,
  "wsUrl": "ws://127.0.0.1:18080",
  "reconnectDelaySeconds": 5,
  "commandPrefix": "/",
  "language": "zh_cn",
  "serverName": "我的服务器",
  "secretFile": "forwarding.secret",
  "backendTimeoutSeconds": 10
}
```

## 部署

1. 先启动中转站，生成 `forwarding.secret`。
2. 复制到插件数据目录 `plugins/qqbot/`。
3. 在后端安装 [QQBot-Paper](https://github.com/ttwlwlbb51522/QQBot-Paper) 插件并确保其处于 Velocity 模式。
4. 启动代理，连接时携带 `X-Forwarding-Secret` 头。

## 依赖

- Velocity 3.x（API 版本与代理版本一致）

## [License](LICENSE)


---