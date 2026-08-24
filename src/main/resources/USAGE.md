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
- `message-blacklist.yml`：聊天和未列入命令白名单的命令内容所使用的关键词或正则表达式。
- `ip-whitelist.yml`：允许出现在消息或命令中的精确 IP、IPv6 或 IPv4 CIDR。
- `violations/YYYY-MM-DD.log`：按服务器本地日期分割的违规记录。

## 违规记录

开启 `config.yml` 的 `log-to-file: true` 后，插件会在 `plugins/SimpChatCensor/violations/` 下按服务器本地日期写入
`YYYY-MM-DD.log`。每行是 5 列制表符分隔内容：时间、玩家 ID（游戏名）、检测类型、规则、原始内容。
日志只记录玩家游戏名，不记录 UUID；原始内容中的换行、回车和制表符会替换为空格，避免破坏列格式。
旧版本根目录的 `violations.log` 不会自动迁移，也不会继续追加；需要时可手工归档或删除。

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

## 聊天刷屏检测

`spam-detection` 只作用于聊天消息，不改变 `/scc` 管理命令和命令根白名单的处理。刷屏检测在普通违禁词扫描前执行，
重复消息或其它刷屏命中会直接取消当前消息，
不受顶部 `action: filter` 或 `action: warn` 设置影响；拥有 `simpchatcensor.bypass` 的玩家不参与刷屏统计。

- 同一玩家在 `duplicate-window-seconds` 内连续发送与上一条完全相同的消息，第二条会被拦截；大小写、空格或标点不同不算完全一致。
- `similar-message` 会比较最近两条消息的规范化共同前缀。共同前缀达到 `minimum-common-prefix-length`，且两条消息在此前缀后的尾部都不超过 `maximum-suffix-length` 时会被拦截，专门处理“每条广告后追加随机字符”的绕过方式。它不是通用相似度算法，较长的正常句子不会因为只有开头相同而触发。
- 一条消息中同一片段连续重复达到 `min-repeats`，且重复总长度达到 `min-total-length`，会被拦截。例如连续发送“神经病”多次。
- `long-message-burst` 会统计滑动时间窗内的长消息；默认 10 秒内第 3 条长度至少 300 个 Java 字符的消息会被拦截，后续仍满足条件的长消息也会拦截。

### 消息和命令最小间隔

`content-minimum-interval` 是硬性限制，没有单独的 `enabled` 开关。插件按玩家记录最近一次聊天或非白名单命令的时间：
当前事件是聊天时使用 `message-millis`，当前事件是命令时使用 `command-millis`。两类事件共用记录，不能通过“聊天、命令交替发送”绕过。
间隔不足时当前消息/命令直接取消，并使用 `DetectionType.SPAM` 记录和通知；不受顶部 `action: filter` 或 `action: warn` 影响。
命令根白名单中的命令，以及拥有 `simpchatcensor.bypass` 的玩家不受此限制。`filter-commands: false` 会关闭整套命令检测，聊天间隔仍然生效。
配置数值会限制在 100 到 60000 毫秒之间。

```yaml
content-minimum-interval:
  message-millis: 800
  command-millis: 250
```

默认配置如下，可按服务器聊天节奏调整：

```yaml
spam-detection:
  enabled: true
  duplicate-window-seconds: 5
  similar-message:
    enabled: true
    window-seconds: 8
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
当前内置库覆盖常见中文/英文辱骂、作弊相关词和常见变体；服主可以直接编辑该文件，或用 `/scc add`、`/scc remove` 管理条目。

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
