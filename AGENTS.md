# SimpChatCensor 项目规范

本文件适用于整个仓库。实现、审查、版本更新、推送和发布都必须遵守这些约束。

## 交付原则

- 先阅读相关实现、测试、配置、文档和工作流，再修改代码；不得只根据问题描述猜测架构。
- 修复根因并保持改动范围清晰。不得通过关闭功能、放宽整个检测器或加入宽泛豁免来掩盖误报。
- 保留工作树中不属于当前任务的改动。禁止强推、移动已发布标签或覆盖现有 Release 资产。
- 面向服主的配置注释、命令反馈和 README 使用中文；代码标识符和提交标题使用清晰英文。
- 所有文本保持 UTF-8 和 LF，遵守 `.gitattributes`，不得提交乱码、IDE 文件或 `target/` 产物。

## 技术基线与架构

- 项目使用 Java 21 和 Maven，运行基线为 Paper 1.21.4+ / Folia 26.1.2+。
- 未经明确要求，不得降低 `java.version`、Paper API、`api-version` 或 Folia 兼容基线。
- `SimpChatCensorPlugin` 只负责生命周期和组件装配，不承载检测或配置业务逻辑。
- 保持现有职责边界：
  - `config`：读取、规范化、持久化和迁移配置；运行时发布不可变 `PluginConfig`。
  - `detection`：纯文本检测、规则编译、GeoIP 查询和下载；逻辑应可独立单元测试。
  - `listener`：读取事件、处理精确豁免、调用检测器，不复制检测规则。
  - `ActionExecutor`：统一执行 `cancel`、`filter`、`warn`、`kick`、日志和通知。
  - `SchedulerUtil`：集中管理 Paper/Folia 调度差异。
  - `BwfCommand`：管理 `/bwf` 的参数、权限、补全和异步操作。
- 新抽象必须减少真实复杂度或匹配既有职责，不能为单次调用引入无意义层级。

## Folia 与并发

- `AsyncChatEvent` 是异步入口。检测链只处理字符串、不可变配置和已编译规则，不得访问世界、
  实体、方块、库存或其他有区域线程所有权的 Bukkit 状态。
- 玩家操作通过 `SchedulerUtil.runForPlayer(...)` 回到实体调度器；全局操作通过
  `SchedulerUtil.runGlobal(...)`。不得在新代码中直接调用 Bukkit scheduler 绕过该边界。
- 枚举在线玩家后如需操作玩家，应为每名玩家进入实体调度器，不能把全局调度器当作任意实体线程。
- 网络请求、数据库访问和大文件 I/O 不得阻塞事件线程、实体线程或全局区域线程。
- 后台任务只捕获稳定数据；回调时考虑插件已禁用、玩家已离线和任务已失效。
- 扫描与 `/bwf reload` 可能并发。新增检测索引必须先完整构建不可变快照，再一次性发布；
  不得在扫描期间原地修改共享集合。
- 新增长期资源、线程或客户端时，必须在重载或 `onDisable` 中明确关闭。

## 配置与数据兼容

- 新增配置项必须同步更新：
  - `src/main/resources/config.yml` 的中文说明和默认值；
  - `ConfigManager#reload` 的 fallback、清洗和边界校验；
  - `PluginConfig` 的不可变字段；
  - README 的用户说明；
  - `src/main/resources/USAGE.md` 的服主说明；
  - 相关测试及测试配置工厂。
- `spam-detection` 的重复消息、重复片段和长消息窗口属于状态型配置；读取时必须限制数值上限，
  运行时状态按玩家 UUID 隔离，并在重载、退出和 bypass 时清理。
- 已生成的旧配置不会自动获得新键，代码 fallback 必须与默认资源一致。
- 保持数据归属：`message-blacklist.yml` 存违禁词，`ip-whitelist.yml` 存 IP/CIDR，
  `config.yml` 存行为选项、域名白名单和 GeoIP 设置。
- 配置迁移必须可重复执行并保留用户数据：清洗空值、忽略大小写去重、先成功保存目标，
  再删除旧键；保存失败时不得破坏原配置。
- 内置违禁词库通过 `message-blacklist.yml` 的 `library-version` 单调合并；只能追加缺失的内置词，
  不得覆盖服主自定义条目或重新加入已删除条目。
- 普通重载不得覆盖服主已有配置或恢复示例值。
- 文件路径必须规范化并限制在插件数据目录内。下载文件要使用临时文件、尺寸或格式检查、
  尽可能原子替换，并在失败时清理。

## 检测契约

- 保持 `BannedWordManager#scan` 的首个命中短路顺序：
  1. 普通关键词或正则；
  2. IP/域名；
  3. 拼音；
  4. Levenshtein。
- 调整顺序会改变动作、日志和用户结果，只能在有明确行为要求和回归测试时进行。
- `use-regex=true` 时逐条编译正则；无效正则记录警告并忽略，不能阻止插件启动。
- `TextPreprocessor` 的小写、高级规范化和 token 化用途不同。调整 Unicode、Leet、标点或
  空白规则时，要同时测试命中绕过和正常文本误报。
- `DetectionResult.detectedText` 保存玩家实际输入，供日志和精确替换使用；`rule` 保存规范化
  IP、域名或配置规则，供白名单、日志和解释使用。不得混用二者。
- 刷屏检测属于聊天监听器的状态型检测，不并入无玩家上下文的 `BannedWordManager#scan`；命中刷屏时必须
  取消当前消息，且不得因为全局 `action` 为 `filter` 或 `warn` 而继续发送。
- IP/域名混淆先规范化再检查白名单；域名只规范化 authority/host，不能改写路径内容。
- 扩大 IP/域名正则或混淆字符集合时，至少增加应命中的绕过样例、规范化 `rule` 断言、
  白名单放行样例，以及相邻正常文本或非法结构不命中的边界样例。
- IPv4 必须保留对嵌入数字串、额外分段和越界八位组的排除。
- 域名白名单只允许精确主机及其子域名，不能用不带点边界的后缀判断。
- 登录地址先检查 IP 白名单，再执行 GeoIP；非空国家允许列表优先于国家黑名单。
- `FILTER` 只有能可靠替换原文时才继续发送；无法定位原文时保持取消并警告，不能放行原内容。
- 新检测类型要同步更新 `DetectionType`、结果构造、动作行为、日志占位符、文档和测试。

## 命令与权限

- `/bwf` 管理权限为 `simpchatcensor.admin`；内容绕过和管理员通知分别使用
  `simpchatcensor.bypass`、`simpchatcensor.notify`。
- `simpchatcensor.bypass` 默认授予 OP，并同时跳过聊天和命令内容检测；通过权限插件撤销或授予时，
  必须在 README 和 USAGE 中明确其影响范围。
- 新增或修改命令、权限时，同步更新执行分支、参数校验、补全、`plugin.yml` 和 README。
- 指令、权限、过滤范围或正则语义变化时，同步更新 `src/main/resources/USAGE.md`；该文件首次启动时
  导出到 `plugins/SimpChatCensor/USAGE.md`，已有说明不会被覆盖。
- `CommandListener` 的判断顺序保持为：功能开关、命令根白名单、玩家 bypass、内容扫描。
- 命令根白名单由 `config.yml` 的 `command-whitelist` 管理，只比较剥离 Bukkit 命名空间后的完整根命令，
  忽略大小写；禁止用 `startsWith`、通配符或前缀规则放行相似命令。
- 新增或调整命令根白名单时，更新默认配置、fallback、README、USAGE 和测试；覆盖普通形式、命名空间形式和
  相似但不得豁免的反例。旧配置缺少该键时的 fallback 必须保持当前默认白名单，显式空列表不得被回填。
- 网络型管理命令要有并发闸门，避免重复下载或同时写入同一文件。

## 测试与构建

- 检测和解析逻辑优先写成无需启动服务器的纯 Java 单元测试，沿用 JUnit 5。
- 每个缺陷至少有一个先失败后通过的回归测试；根据风险补充误报、白名单、边界和动作断言。
- 不只断言“存在结果”，按契约检查 `DetectionType`、`detectedText`、`rule` 和动作。
- 配置字段或默认值变化时，更新所有 `PluginConfig` 测试工厂。
- 配置迁移变化应覆盖目标不存在、已有目标合并、重复迁移和保存失败保留旧值。
- 修改事件、调度或生命周期时，说明纯单元测试未覆盖的 Paper/Folia 服务器风险；条件允许时增加
  真实服务器冒烟验证。
- 提交前至少执行：

```powershell
mvn -B -ntp clean verify
git diff --check
```

- 正式产物只有 `target/SimpChatCensor-<version>.jar`。不得安装或发布
  `target/original-SimpChatCensor-*.jar`。
- 打包后核对内嵌 `plugin.yml` 版本，并确认 pinyin4j、MaxMind、Commons Net 仍被重定位到
  `com.simpchatcensor.libs.*`。

## 版本号规范

- 新版本使用语义化格式 `MAJOR.MINOR.PATCH`（数字段不得有前导零），预发布后缀只使用 `-alpha[.N]`、
  `-beta[.N]` 或 `-rc[.N]`。历史 `1.x-beta` 标签保持不变，新版本不得继续省略 PATCH。
- 版本选择遵循最小必要增量：
  - `MAJOR`：不兼容的配置语义、命令/权限契约、运行基线或用户行为变更；
  - `MINOR`：向后兼容的新功能、新命令、新配置或检测能力；
  - `PATCH`：向后兼容的缺陷、误报、安全、性能和可靠性修复。
- 项目仍处于预发布阶段时保留当前阶段后缀；只有明确决定稳定发布时才移除后缀。
- 同一交付包含多类变化时使用最高等级，只升一次版本。
- 用户可见运行时行为、配置默认值或结构、命令/权限、兼容基线、运行时依赖，以及需要服主安装的
  新修复都必须在同一交付中升版。
- 仅文档、测试、CI、仓库规范或不改变交付行为的内部重构不升插件版本；但如果分支中已有尚未
  发布的运行时变化，必须先为整批变化升版再发布。
- 仅留在仓库中的说明属于文档变更；如果说明被打包进 JAR 并在首次启动导出到插件数据目录，
  则属于用户可见交付，至少按 PATCH 升版并通过发布流程交付。
- 用户未指定目标版本时，按上述规则选择最小合法增量，并在交付说明中明确理由。
- 每次升版必须同步修改 `pom.xml` 项目版本和 `src/main/resources/plugin.yml` 版本；README
  和 `src/main/resources/USAGE.md` 若存在固定版本示例也要同步。两处运行时版本必须逐字一致。
- 禁止发布 `SNAPSHOT`，禁止复用已发布版本，禁止移动或覆盖任何已有 `v<version>` 标签和资产。

## 提交、推送与自动发布

- 代码审查或诊断任务不产生提交。已授权的实现任务完成并通过验证后，除非用户明确要求只保留
  本地，提交并推送 `origin/main`，不要停在未交付的本地 diff。
- 发布由版本值变化驱动，不能由工作流猜测或自动递增版本。发布负责人或实现代理先按规范选择并
  同步版本，推送 `main` 后由 `.github/workflows/build.yml` 自动完成其余步骤。
- 普通提交版本未变化时只构建和测试，不创建标签或 Release。
- 版本变化提交通过构建后，工作流自动：
  1. 验证版本格式与两处元数据一致；
  2. 验证最终 shaded JAR 及其内嵌 `plugin.yml`；
  3. 生成只包含正式 JAR 的 `SHA256SUMS.txt`；
  4. 创建指向该 `main` 提交的 annotated tag `v<version>`；
  5. 创建 GitHub Release 并上传正式 JAR 与校验和；
  6. 对 `alpha`、`beta`、`rc` 自动标记 prerelease。
- 正常发布不得手工创建 `v*` 标签。自动流程失败时优先重跑原 workflow；需要恢复时可手动运行
  workflow 并输入与当前 POM 完全一致的版本，但仍不得移动冲突标签或覆盖已发布资产。
- Release job 只能发布可从 `origin/main` 到达的提交；标签已存在且指向其他提交时必须失败。
- Release notes 使用 GitHub 自动生成内容，不使用固定占位文本。
- 发布后必须等待并验证：Build/Release workflow 成功；分支和标签指向正确；Release 非 draft；
  预发布状态正确；资产严格为一个正式 JAR 和 `SHA256SUMS.txt`；下载后重新计算 SHA-256 并核对
  内嵌版本；工作树干净且没有未推送提交。

## 完成定义

- 用户点名的问题和后续补充均已实现，没有静默缩小范围。
- 行为变化具备相称的回归测试，配置和文档与实现一致。
- 完整 Maven 构建、差异检查和产物检查通过；未运行的环境验证要明确说明。
- 需要发布时版本、提交、远端分支、标签、Release、资产和校验和全部核验完成。
