# SimpChatCensor 使用说明

本文件由插件随 JAR 发布，并在首次启动时生成到 `plugins/SimpChatCensor/USAGE.md`。
已有文件不会被覆盖；升级后想重新生成最新版说明时，请先备份并删除该文件，再重启插件。
它是说明文件，不是配置文件，请把自定义内容写入下面的 YAML 文件。

## 快速开始

1. 启动一次服务器，让插件生成配置文件。
2. 编辑 `config.yml`、`message-blacklist.yml` 和 `ip-whitelist.yml`。
3. 执行 `/scc reload` 让修改立即生效。
4. 用 `/scc list` 检查当前消息黑名单。

三个文件的用途如下：

- `config.yml`：处理动作、开关、命令根白名单、提示文本、拼音/编辑距离、IP/域名和 GeoIP 设置。
- `message-blacklist.yml`：聊天和未列入命令白名单的命令内容所使用的关键词或正则表达式；Emoji 由内置检测直接拦截，无需加入黑名单。
- `ip-whitelist.yml`：允许出现在消息或命令中的精确 IP、IPv6 或 IPv4 CIDR。
- `logs/YYYY-MM-DD.log`：按服务器本地日期分割的违规记录。

## 违规记录

开启 `config.yml` 的 `log-to-file: true` 后，插件会在 `plugins/SimpChatCensor/logs/` 下按服务器本地日期写入
`YYYY-MM-DD.log`。每行是 5 列制表符分隔内容：时间、玩家 ID（游戏名）、检测类型、规则、原始内容。
日志只记录玩家游戏名，不记录 UUID；原始内容中的换行、回车和制表符会替换为空格，避免破坏列格式。
旧版本的 `violations/` 目录和根目录 `violations.log` 不会自动迁移，也不会继续追加；需要时可手工归档或删除。
违规记录由后台单线程顺序写入，避免磁盘 I/O 阻塞聊天；插件关闭时会等待队列刷完，极端洪泛导致队列满时会记录一次警告并丢弃超出的记录。

控制台拦截记录使用简洁的三行摘要，格式与 ChatFilter 类似：

```text
------- Match Type: DOMAIN ~ CMD
Match: cnpvp.top
Catch > 玩家名: 原始命令或消息
```

来源标记为 `CHAT`、`CMD` 或 `LOGIN`；换行、回车和制表符会显示为空格，不会破坏控制台输出。

## 玩家提示

`config.yml` 中的 `player-warning` 和 `spam-warning` 分别处理两类情况：

- `player-warning`：关键词、正则、IP/域名、拼音或编辑距离等内容检测命中时使用。
- `spam-warning`：重复消息、重复片段、随机后缀相似消息、长消息突发，以及聊天/命令发送过快时使用。

刷屏检测命中仍然会直接取消当前消息或命令，不会因为顶部 `action` 设置为 `filter` 或 `warn` 而放行；这只改变玩家看到的提示文本。
旧版配置没有 `spam-warning` 时，插件会使用 `&e消息发送过快，请稍后再试。` 作为 fallback，不会覆盖已有的 `player-warning`。

## Emoji 检测

插件会在关键词/正则、IP/域名、拼音和编辑距离阶段之后直接检查 Emoji。只要消息或命令包含实际 Emoji 字符或完整 Emoji 序列，
就会使用 `DetectionType.EMOJI` 记录并取消当前内容；它不受顶部 `action: filter` 或 `action: warn` 设置影响。
例如 `你🐎死了` 会命中 `🐎`。检测会保留完整序列，包括变体选择符、肤色修饰符、双区域指示符旗帜、键帽和 ZWJ 序列，过滤后不会残留连接符。
普通文本符号（`©`、箭头和不带 `U+FE0F` 的 `❤`）不会被当作 Emoji；需要显式 Emoji 展示时才会拦截，例如 `❤️`。`™` 虽不是 Emoji，但已作为内置黑名单词条单独拦截。

## 聊天刷屏检测

`spam-detection` 只作用于聊天消息，不改变 `/scc` 管理命令和命令根白名单的处理。消息先进行不写入历史的刷屏预检，未命中时再进行普通违禁词扫描；
内容最终放行后会再次确认刷屏状态并更新历史，避免并发消息绕过限速。重复消息或其它刷屏命中会直接取消当前消息，不受顶部 `action: filter` 或 `action: warn` 设置影响。
被内容规则拦截或被刷屏规则取消的消息不会计入后续刷屏统计，也不会延长已有记录的有效时间；只有实际放行的消息才会更新历史。
历史按玩家隔离，保存最近至少 15 秒内所有放行的聊天；拥有 `simpchatcensor.bypass` 的玩家不参与刷屏统计。

- 当前消息与 `duplicate-window-seconds` 内任意一条已放行聊天完全相同时会被拦截；大小写、空格或标点不同不算完全一致。
- `similar-message` 会与其 `window-seconds` 内每条已放行聊天比较，先转为小写并去除首尾空白。共同前缀达到 `minimum-common-prefix-length`，且两条消息在此前缀后的尾部都不超过 `maximum-suffix-length` 时会被拦截；规范化后完全一致且达到最短前缀长度也会被拦截。它专门处理“每条广告后追加随机字符”的绕过方式，不是通用编辑距离相似度算法，较长的正常句子不会因为只有开头相同而触发。超过 4096 个 Java 字符的消息不进行相似比较，仍参与精确重复检查；超长消息使用摘要保存精确重复记录。
- 一条消息中同一片段连续重复达到 `min-repeats`，且重复总长度达到 `min-total-length`，会被拦截。例如连续发送“神经病”多次。
- `long-message-burst` 会统计滑动时间窗内的长消息；默认 10 秒内第 3 条长度至少 300 个 Java 字符的消息会被拦截，后续仍满足条件的长消息也会拦截。

例如在 15 秒内发送 `你好你好你好 → 1 → 你好你好你好 → 2 → 你好你好你好 → 3`，第二次及后续的 `你好你好你好` 仍会命中精确重复检测；
数字消息不会替换或清除此前的历史。相似消息中插入其它内容同样不能绕过检测。
两个消息窗口默认均为 15 秒，可分别配置为 15 到 3600 秒，各自只比较自身窗口内的记录；
旧配置中的 5 秒、8 秒或其它小于 15 秒的值会在运行时按 15 秒处理，不改写服主文件。可以单独关闭 `similar-message.enabled`；
关闭 `spam-detection.enabled` 后不缓存聊天历史，但硬性最小间隔仍然生效。
缓存按两个已启用窗口的最大值保留，每名玩家最多保存 256 条，原文和规范化副本累计最多保留 131072 个字符；
达到任一容量上限时会拦截新消息，等旧记录过期后恢复，不会挤掉仍有效的记录。
默认 15 秒窗口中，即使用允许的最小 100 毫秒间隔发送常规 256 字符消息也不会触及上限；
配置更长窗口或发送超长消息时可能触及，服主应结合聊天量设置。

### 消息和命令最小间隔

`content-minimum-interval` 是硬性限制，没有单独的 `enabled` 开关。插件只记录最终放行的聊天或非白名单命令，并按玩家保存最近一次活动时间：
当前事件是聊天时使用 `message-millis`，当前事件是命令时使用 `command-millis`。两类事件共用记录，不能通过“聊天、命令交替发送”绕过。
间隔不足时当前消息/命令直接取消，并使用 `DetectionType.SPAM` 记录和通知；不受顶部 `action: filter` 或 `action: warn` 影响。
命令根白名单中的命令，以及拥有 `simpchatcensor.bypass` 的玩家不受此限制。`filter-commands: false` 会关闭整套命令检测，聊天间隔仍然生效。
配置数值会限制在 100 到 60000 毫秒之间。

```yaml
content-minimum-interval:
  message-millis: 200
  command-millis: 250
```

聊天消息默认最短间隔为 200 毫秒，受检命令默认保持 250 毫秒。已有 `config.yml` 不会在重载时被覆盖；升级后如需使用新默认值，请手动修改对应字段。

默认配置如下，可按服务器聊天节奏调整：

```yaml
spam-detection:
  enabled: true
  duplicate-window-seconds: 15
  similar-message:
    enabled: true
    window-seconds: 15
    minimum-common-prefix-length: 12
    maximum-suffix-length: 64
  repeated-content:
    enabled: true
    min-repeats: 4
    min-segment-length: 1
    max-segment-length: 16
    min-total-length: 12
  long-message-burst:
    enabled: true
    min-length: 300
    max-messages: 3
    window-seconds: 10
```

配置数值会被限制在合理范围内，防止异常配置造成过高 CPU 或内存开销。玩家退出、插件重载或获得 bypass 权限后，
该玩家的临时刷屏历史会清除。内置违禁词库位于 `message-blacklist.yml`；升级时会按 `library-version` 合并新增词条，
保留服主自己的词条和删除选择，比较时忽略大小写并去重。
当前内置库覆盖常见中文/英文辱骂、谐音变体、作弊和游戏账号广告词；例如“嘛的”作为独立词条可在关闭拼音检测时仍被识别。服主可以直接编辑该文件，或用 `/scc add`、`/scc remove` 管理条目。升级会按 `library-version` 增量合并新词；不希望拦截的词可从文件中删除，之后重载即可。
高级预处理会移除词语内部的短断句标点；域名检测支持字母或数字间的短空格分隔，例如 `mc 2 4 4 . c o m`。
明确的外挂交易短语已加入内置库；“开挂”仍不作为单独词条，以免误伤“开挂人生”等正常表达。升级会保留旧文件中的服主自定义条目和删除选择；如果某个新增词不适合你的服务器，执行 `/scc remove <词>`，
或编辑 `message-blacklist.yml` 后执行 `/scc reload`。

## 管理指令

所有 `/scc` 子指令都需要 `simpchatcensor.admin` 权限，默认只有 OP 拥有。
不带参数或输入未知子指令时会显示简要帮助。

| 指令 | 作用 |
| --- | --- |
| `/scc reload` | 完整重载配置、黑名单、白名单和已有 GeoIP 数据库。 |
| `/scc add <词或正则>` | 追加一条消息黑名单，成功后自动重载。参数从第二项开始会用一个空格拼接。 |
| `/scc remove <词或正则>` | 按完整条目、不区分大小写删除一条黑名单；不是部分删除，也不会把参数当正则执行。 |
| `/scc list` | 显示黑名单数量和条目，每行最多显示 12 项，没有页码参数。 |
| `/scc whitelist add <IP或域名>` | 添加白名单并重载。IP/CIDR 写入 `ip-whitelist.yml`，其它值写入 `config.yml` 的域名白名单。 |
| `/scc geoip reload` | 重新读取本地 GeoIP 数据库，不下载文件。 |
| `/scc geoip update` | 按 `config.yml` 的下载地址异步下载 GeoIP 数据库，完成后自动重载。 |

`geoip update` 同时只允许一个任务；再次执行会提示稍后重试。网络失败、HTTP 非 2xx、文件过小或替换失败时，原数据库会保留。
目前没有 `whitelist remove` 指令；删除白名单请编辑对应 YAML 后执行 `/scc reload`。

### 权限节点

- `simpchatcensor.admin`：使用 `/scc` 管理指令，默认 `op`。
- `simpchatcensor.bypass`：该玩家跳过聊天和普通命令内容检测，默认 `op`。权限插件可对个别 OP 撤销，或授予非 OP。
- `simpchatcensor.notify`：接收违规通知，默认 `op`；它不等于管理权限。

命令内容由 `config.yml` 的 `command-whitelist` 白名单决定是否免检。默认条目为 `scc`、`lp`、`luckperms`，
会精确匹配 `/scc`、`/lp`、`/luckperms` 及它们的 Bukkit 命名空间形式，避免把 LuckPerms 权限节点误识别为域名。
相似但不同的根命令不会被宽泛放行；`filter-commands: true` 时，未列出的命令仍会参与检测。
该白名单跳过整条命令的违规检测，不是 IP/域名白名单；不要把 `/msg`、`/tell`、`/mail` 等可转发玩家内容的命令加入默认列表。
`whitelist add` 只按字符串外形粗略区分 IP 和域名，不会在命令阶段完整验证地址；无效值请直接修正 YAML 后重载。

拼音模糊匹配按连续文字词段完整匹配，避免“干嘛的”因包含“妈的”的拼音而误报；独立的“你嘛”“你吗”仍可命中“你妈”。

## 基本配置

```yaml
# config.yml
action: cancel       # cancel / filter / warn / kick
use-regex: false     # true 时，message-blacklist.yml 的每一项都是 Java 正则
advanced-preprocess: true
filter-commands: true
command-whitelist:
  - scc
  - lp
  - luckperms
```

`command-whitelist` 建议只写命令根名，不带 `/`、命名空间或参数；匹配时忽略大小写并自动剥离 Bukkit 命名空间，
因此旧写法中的 `/lp` 或 `luckperms:lp` 也会被兼容规范化。旧配置没有该键时会使用上述默认值。
显式写成 `command-whitelist: []` 时，没有命令会跳过检测；若误写成非 YAML 列表，插件会记录警告并按空白名单处理。修改后执行 `/scc reload`。

处理动作含义：

- `cancel`：拦截消息或命令。
- `filter`：把命中的原文替换为 `***` 后继续发送；无法可靠定位时会取消并警告。
- `warn`：只提示玩家，不拦截。
- `kick`：踢出玩家。

IP/域名检测、拼音检测和编辑距离检测有各自的开关；打开正则不会关闭它们。
普通的 IP、域名和混淆分隔符优先使用内置检测与白名单，不必为它们重复编写正则。
域名还必须满足 `config.yml` 中 `ip-domain-detection.domain-suffixes` 的后缀列表。默认随插件打包 IANA 根区全量后缀，包含
`zip`、`uk`、`hk`、`ai`、`cloud`、`tech` 和 `xn--` 国际化后缀，所以 `cnpvp,top` 会先规范化为 `cnpvp.top` 再命中。列表项可以写顶级后缀（`com`）或多级后缀
（`co.uk`），不需要前导点；显式写成 `[]` 时不会命中域名。这个约束只影响域名，不影响 IPv4、IPv6 和 GeoIP 检测。
域名白名单仍然优先按完整主机及其子域名判断，例如 `minecraft.net` 会放行 `auth.minecraft.net`。

## 正则表达式

### 开关和匹配方式

在 `config.yml` 设置 `use-regex: true` 后，`message-blacklist.yml` 中的每一项都会按 Java `Pattern` 编译，使用
`CASE_INSENSITIVE | UNICODE_CASE`，并在原始消息上执行 `Matcher.find()`。只要正则在消息或命令中找到一段匹配就会触发，
不要求整条消息完全相等。正则未命中时，才会依次检查 IP/域名、拼音和编辑距离；任一阶段命中后会立即停止并返回首个结果。
正则阶段不会使用 `advanced-preprocess` 的 Unicode、Leet 或空白归一化；需要这些变体时依靠独立的拼音/编辑距离检测。

```yaml
# message-blacklist.yml
banned-words:
  - '违规词'
  - '(?:qq|vx|微信)\s*[:：]?\s*[1-9]\d{4,11}'
```

建议使用 YAML 单引号包住正则。单引号会保留反斜杠，`\d`、`\s`、`\.` 可以直接写；如果使用 YAML 双引号，反斜杠通常要再写一层，例如 `- "\\d+"`。
复杂规则建议直接编辑 `message-blacklist.yml`，再执行 `/scc reload`。

### 常用语法

| 写法 | 含义 | 示例 |
| --- | --- | --- |
| `.` | 任意单个字符 | `a.c` |
| `\d` / `\s` / `\w` | 数字 / 空白 / 字母数字下划线 | `\d{5,12}` |
| `*`、`+`、`?` | 重复 0 次以上、1 次以上、0 或 1 次 | `ab+c` |
| `{m,n}` | 重复次数范围 | `\d{5,12}` |
| `[abc]`、`[^abc]` | 字符集合 / 排除集合 | `[A-Z0-9_]` |
| `(a|b)` | 分组和二选一 | `(qq|vx)` |
| `(?:...)` | 不捕获分组 | `(?:qq|vx)` |
| `\b` | 单词边界；混合中文时优先使用 lookaround | `\bfoo\d{2}\b` |
| `^`、`$` | 消息开头 / 结尾 | `^广告$` |
| `\.` | 匹配字面量句点 | `example\.com` |
| `(?<!...)`、`(?!...)` | 前后不能出现指定内容 | `(?<![A-Za-z0-9_])bad(?![A-Za-z0-9_])` |

### 可直接使用的例子

```yaml
banned-words:
  # 独立的英文单词，不匹配 badger
  - '(?<![A-Za-z0-9_])bad(?![A-Za-z0-9_])'
  # 允许字母、空格、点、下划线或短横线穿插
  - 'b[\s._-]*a[\s._-]*d'
  # QQ、VX 或微信号，后面接 5 到 12 位数字
  - '(?:qq|vx|微信)\s*[:：]?\s*[1-9]\d{4,11}'
  # 域名和可选端口；普通域名由内置域名检测覆盖，只有需要放行合法域名时才加入 domain-whitelist
  - '(?:play\.)?example\.com(?::\d{1,5})?'
  # 整条消息只有“广告”（允许两侧空白）
  - '^\s*广告\s*$'
```

注意事项：

1. 正则模式不能和普通关键词模式混用；`use-regex: true` 后列表中的所有条目都按正则解释。
   想匹配字面量 `.`、`+`、`?`、`(`、`)`、`[`、`]`、`{`、`}`、`|` 或反斜杠时，要使用反斜杠转义，例如 `example\.com`。
2. `Matcher.find()` 会查找子串；不需要为了“任意位置”再写 `.*`。`.*`、`a*`、`^`、`$` 等可能匹配空字符串，
   会造成几乎所有消息触发，且 `filter` 无法定位有效文本，应避免使用。
3. 避免 `(.*)+`、`(.+)*` 等嵌套重复和没有边界的超长通配符，它们可能造成严重回溯并拖慢检测。
4. 无效 Java 正则不会阻止插件启动；重载时会在控制台记录警告并忽略该条目。修正文件后再次执行 `/scc reload`。
5. `/scc add` 不会提前验证正则语法；命令显示“已添加”不代表正则一定有效，最终以重载日志为准。

## 修改后检查

- 修改 `config.yml`、`message-blacklist.yml` 或 `ip-whitelist.yml` 后执行 `/scc reload`。
- 用 `/scc list` 确认条目已保存；正则错误查看服务器控制台的 `Invalid banned regex ignored` 警告。
- 先用小范围测试消息验证 `cancel`/`filter`/`warn`/`kick` 行为，再扩大规则。
- 如果只是误报某个合法 IP 或域名，优先添加精确白名单或收紧正则边界，不要为了绕过误报关闭整个检测器。
- 如果是管理命令中的合法标识符被误报，只把该命令的完整根名加入 `command-whitelist`，不要使用通配符或相似前缀。
