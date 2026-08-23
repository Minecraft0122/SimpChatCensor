# SimpChatCensor

SimpChatCensor 是一个面向 Paper 1.21.4+ / Folia 26.1.2+ 的聊天与命令违禁内容过滤插件。

## 功能

- 普通关键词与正则匹配
- Unicode 规范化、变音剥离、Leet 还原、空白移除
- 拼音模糊匹配，覆盖“你妈/你嘛/你吗”这类同音变体
- 编辑距离模糊匹配
- IPv4、IPv6、域名及 IP/域名混淆分隔符检测
- IP 默认拦截，仅允许 `ip-whitelist.yml` 中的精确 IP 或 IPv4 CIDR 网段
- 可选 GeoIP 国家/地区登录来源拦截
- 支持 `cancel`、`filter`、`warn`、`kick` 处理动作
- Folia 调度兼容
- 聊天重复消息、随机后缀相似消息、连续重复片段和短时间长消息刷屏检测
- 可配置的聊天/命令硬性最小间隔，防止外挂通过随机字符绕过刷屏规则
- 内置中文/英文辱骂与作弊广告词库，并在升级时增量合并

## 构建

```powershell
mvn -B -ntp clean verify
```

构建产物位于 `target/SimpChatCensor-<版本号>.jar`。

## 自动发布

`main` 上的项目版本发生变化时，GitHub Actions 会在完整构建通过后自动创建
`v<版本号>` 标签和 GitHub Release，并上传正式 JAR 与 `SHA256SUMS.txt`。
普通提交不会发版；不要手工创建发布标签。`alpha`、`beta`、`rc` 版本会自动标记为预发布。

完整的开发、测试、版本号和发布要求见 [AGENTS.md](AGENTS.md)。

## 命令

```text
/bwf reload
/bwf add <词/正则>
/bwf remove <词/正则>
/bwf list
/bwf geoip reload
/bwf geoip update
/bwf whitelist add <IP/域名>
```

OP 默认拥有 `simpchatcensor.bypass`，会跳过聊天和命令内容检测；可通过权限插件按需显式撤销或授予。
命令内容使用 `config.yml` 的 `command-whitelist` 精确根命令白名单处理。默认白名单为
`bwf`、`lp`、`luckperms`，支持 Bukkit 命名空间形式，避免把 LuckPerms 权限节点误识别为域名；
未列出的命令在 `filter-commands: true` 时先执行硬性最小间隔限制，再参与内容检测；关闭该开关会关闭整套命令检测。
`content-minimum-interval` 没有关闭开关，聊天与受检命令共用同一玩家活动时间；白名单命令和
拥有 `simpchatcensor.bypass` 的玩家不受该限制。
插件首次启动时会生成 `plugins/SimpChatCensor/USAGE.md`，其中包含完整指令、权限和 Java 正则示例。

## 配置文件

- `config.yml`：过滤选项、命令根白名单、处理动作、提示文本、域名白名单与 GeoIP 设置
- `message-blacklist.yml`：消息违禁词/正则列表
- `ip-whitelist.yml`：允许出现在消息或命令中的 IP；未列出的 IP 默认全部拦截
- `violations/YYYY-MM-DD.log`：按服务器本地日期分割的违规记录；每行只记录时间、玩家 ID（游戏名）、检测类型、规则和原始内容，不记录 UUID

`config.yml` 中的 `player-warning` 只用于违禁内容提示；刷屏、重复消息和聊天/命令发送过快时使用独立的
`spam-warning`，避免把限速提醒显示成违禁词警告。旧配置缺少该键时，运行时会回退到
`&e消息发送过快，请稍后再试。`。

从旧版本升级时，原 `config.yml` 中的 `banned-words` 和 `ip-domain-detection.ip-whitelist`
会在首次加载时自动合并到两个新文件中；旧的 CIDR/IP 黑名单配置会被移除。

聊天刷屏规则和完整词库说明见插件首次启动生成的 `plugins/SimpChatCensor/USAGE.md`。刷屏命中会直接取消消息；
OP 默认拥有 `simpchatcensor.bypass`，不会参与聊天内容和刷屏统计。

域名检测会先规范化逗号、中文句号等混淆分隔符，再要求域名最后的后缀出现在
`ip-domain-detection.domain-suffixes` 中；默认列表包含 `com`、`net`、`org`、`cn`、`top` 等常见后缀，
因此 `cnpvp,top` 仍会被识别。可按服务器实际需要增加后缀，也可以填写多级后缀（例如 `co.uk`）。
显式设置为空列表时不会命中任何域名，但 IP 检测仍然有效；域名白名单仍按精确主机及其子域名生效。

启用 `log-to-file` 后，违规记录写入插件目录下的 `violations` 文件夹，每天生成一个 `YYYY-MM-DD.log` 文件。
日志中的玩家 ID 是游戏名，不写入 UUID；旧版本根目录下的 `violations.log` 不会再追加，历史文件可由服主自行归档。

控制台拦截记录采用简洁的三行摘要，格式与 ChatFilter 类似：

```text
------- Match Type: DOMAIN ~ CMD
Match: cnpvp.top
Catch > 玩家名: 原始命令或消息
```

其中 `CHAT`、`CMD`、`LOGIN` 分别表示聊天、命令和登录来源；原始内容中的换行、回车和制表符会显示为空格。

`/bwf geoip update` 默认从 [P3TERX/GeoLite.mmdb](https://github.com/P3TERX/GeoLite.mmdb) 下载 `GeoLite2-Country.mmdb`，保存到插件数据目录并自动重载。GeoLite2 数据库由 MaxMind 提供，请遵守其许可要求。

GeoIP 只用于玩家登录 IP 的国家/地区限制。它适合遭遇特定地区攻击、或需要国家白名单/黑名单的服务器；如果你只是要拦截聊天里的 IP/域名广告，可以保持 `geoip-enabled: false`。

拼音模糊匹配默认开启。旧版本已经生成过配置文件的服务器，需要确认 `fuzzy-pinyin.enabled` 是 `true`。
