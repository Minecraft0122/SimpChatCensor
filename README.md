# SimpChatCensor

SimpChatCensor 是一个面向 Paper 1.21.4+ / Folia 26.1.2+ 的聊天与命令违禁内容过滤插件。

## 功能

- 普通关键词与正则匹配
- Emoji 字符和完整 Emoji 序列直接拦截（例如 `你🐎死了` 中的 `🐎`）
- Unicode 规范化、变音剥离、Leet 还原、空白和短断句符号移除
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

## 许可证

本项目采用 GNU 通用公共许可证第三版（GPL-3.0-only）授权，详见 [LICENSE](LICENSE)。
项目依赖的第三方库保留其各自许可证；重新分发时请同时遵守这些依赖的授权条款。

## 命令

```text
/scc reload
/scc add <词/正则>
/scc remove <词/正则>
/scc list
/scc geoip reload
/scc geoip update
/scc whitelist add <IP/域名>
```

OP 默认拥有 `simpchatcensor.bypass`，会跳过聊天和命令内容检测；可通过权限插件按需显式撤销或授予。
命令内容使用 `config.yml` 的 `command-whitelist` 精确根命令白名单处理。默认白名单为
`scc`、`lp`、`luckperms`，支持 Bukkit 命名空间形式，避免把 LuckPerms 权限节点误识别为域名；
未列出的命令在 `filter-commands: true` 时先执行硬性最小间隔预检，再参与内容检测；只有内容最终放行的命令才会更新共享活动记录；关闭该开关会关闭整套命令检测。
`content-minimum-interval` 没有关闭开关，聊天与受检命令共用同一玩家活动时间；白名单命令和
拥有 `simpchatcensor.bypass` 的玩家不受该限制。
默认聊天消息最短间隔为 200 毫秒，受检命令保持 250 毫秒；已有配置不会被重载自动覆盖，升级后如需使用新默认值请手动修改 `config.yml`。
插件首次启动时会生成 `plugins/SimpChatCensor/USAGE.md`，其中包含完整指令、权限和 Java 正则示例。

## 配置文件

- `config.yml`：过滤选项、命令根白名单、处理动作、提示文本、域名白名单与 GeoIP 设置
- `message-blacklist.yml`：消息违禁词/正则列表
- `ip-whitelist.yml`：允许出现在消息或命令中的 IP；未列出的 IP 默认全部拦截
- `logs/YYYY-MM-DD.log`：按服务器本地日期分割的违规记录；每行只记录时间、玩家 ID（游戏名）、检测类型、规则和原始内容，不记录 UUID

`config.yml` 中的 `player-warning` 只用于违禁内容提示；刷屏、重复消息和聊天/命令发送过快时使用独立的
`spam-warning`，避免把限速提醒显示成违禁词警告。旧配置缺少该键时，运行时会回退到
`&e消息发送过快，请稍后再试。`。

Emoji 会在关键词、IP/域名、拼音和编辑距离检测之后作为独立阶段检查，并始终直接取消包含 Emoji 的聊天或命令，
不受顶部 `action: filter` 或 `action: warn` 影响。检测结果类型为 `emoji`，会提取完整的 Emoji 序列（包括变体选择符、肤色修饰符、旗帜、键帽和 ZWJ 家庭序列），
因此过滤不会留下不可见的连接符。普通文本符号（例如 `©`、`™`、箭头和不带变体选择符的 `❤`）不会按 Emoji 拦截。

内置库会拦截常见辱骂、作弊广告和断句变体，并把 `™` 作为黑名单词条处理；域名检测也支持字符间短空格混淆。升级时会保留服主自己的黑名单条目；如果旧的 `message-blacklist.yml` 中仍有“开挂”，
请执行 `/scc remove 开挂`，或直接编辑文件后执行 `/scc reload`。

从旧版本升级时，原 `config.yml` 中的 `banned-words` 和 `ip-domain-detection.ip-whitelist`
会在首次加载时自动合并到两个新文件中；旧的 CIDR/IP 黑名单配置会被移除。

聊天刷屏检测会按玩家缓存最近至少 15 秒内所有放行的聊天，并与窗口内每一条消息检查完全重复和随机后缀相似。
例如发送 `你好你好你好 → 1 → 你好你好你好 → 2` 时，第二次 `你好你好你好` 仍会被拦截，插入数字不能清掉之前的记录。
完全重复由 `spam-detection.duplicate-window-seconds` 控制，相似比较由 `spam-detection.similar-message.window-seconds` 控制；
两者默认均为 15 秒，可分别设置为 15 到 3600 秒。旧配置中的 5 秒、8 秒或其它低于 15 秒的值会在运行时按 15 秒处理，不改写文件。
相似比较忽略大小写和首尾空白，检查足够长的共同前缀及短尾部；规范化后完全一致且达到最短前缀长度也会被拦截。
它不是通用编辑距离相似度检测；超过 4096 个 Java 字符的消息仍参与精确重复检查，但不进行相似比较。
可以单独关闭 `similar-message.enabled`，关闭整个 `spam-detection.enabled` 则不缓存聊天历史；硬性最小间隔仍然生效。
历史按两个已启用窗口的最大值保留，每名玩家最多 256 条，原文和规范化副本累计最多保留 131072 个字符；
达到任一容量上限时暂时拦截新消息，等旧记录过期后恢复，避免插入大量内容挤掉历史。
默认 15 秒窗口中，即使用允许的最小 100 毫秒间隔发送常规 256 字符消息也不会触及上限；使用更长窗口或超长消息时可能触及。
被内容规则拦截或被刷屏规则取消的消息不会计入后续刷屏统计，也不会延长已有记录的有效时间；只有实际放行的消息才会更新历史。
玩家退出、插件重载或进入 bypass 状态时会清理历史。完整规则和词库说明见插件首次启动生成的 `plugins/SimpChatCensor/USAGE.md`。
OP 默认拥有 `simpchatcensor.bypass`，不会参与聊天内容和刷屏统计。

域名检测会先规范化逗号、中文句号等混淆分隔符，再要求域名最后的后缀出现在
`ip-domain-detection.domain-suffixes` 中；默认随插件打包 IANA 根区全量后缀，因此包含 `zip`、`uk`、`hk`、`ai`、`cloud`、`tech` 和 `xn--` 国际化后缀，
因此 `cnpvp,top` 仍会被识别。可按服务器实际需要增加后缀，也可以填写多级后缀（例如 `co.uk`）。
显式设置为空列表时不会命中任何域名，但 IP 检测仍然有效；域名白名单仍按精确主机及其子域名生效。

启用 `log-to-file` 后，违规记录写入插件目录下的 `logs` 文件夹，每天生成一个 `YYYY-MM-DD.log` 文件。
日志中的玩家 ID 是游戏名，不写入 UUID；旧版本的 `violations/` 目录和根目录 `violations.log` 不会自动迁移，也不会继续追加，历史文件可由服主自行归档。
违规记录由后台单线程顺序写入，避免磁盘 I/O 阻塞聊天；插件关闭时会等待队列刷完，极端洪泛导致队列满时会记录一次警告并丢弃超出的记录。

控制台拦截记录采用简洁的三行摘要，格式与 ChatFilter 类似：

```text
------- Match Type: DOMAIN ~ CMD
Match: cnpvp.top
Catch > 玩家名: 原始命令或消息
```

其中 `CHAT`、`CMD`、`LOGIN` 分别表示聊天、命令和登录来源；原始内容中的换行、回车和制表符会显示为空格。

`/scc geoip update` 默认从 [P3TERX/GeoLite.mmdb](https://github.com/P3TERX/GeoLite.mmdb) 下载 `GeoLite2-Country.mmdb`，保存到插件数据目录并自动重载。GeoLite2 数据库由 MaxMind 提供，请遵守其许可要求。

GeoIP 只用于玩家登录 IP 的国家/地区限制。它适合遭遇特定地区攻击、或需要国家白名单/黑名单的服务器；如果你只是要拦截聊天里的 IP/域名广告，可以保持 `geoip-enabled: false`。

拼音模糊匹配默认开启，并按连续文字词段完整匹配，避免“干嘛的”因包含“妈的”的拼音而误报。
旧版本已经生成过配置文件的服务器，需要确认 `fuzzy-pinyin.enabled` 是 `true`。
