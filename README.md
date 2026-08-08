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

`/bwf` 管理命令会自动绕过内容扫描，因此可以正常添加白名单与消息黑名单条目。
`/lp`、`/luckperms`（含命名空间形式）同样会绕过扫描，避免把权限节点误识别为域名。

## 配置文件

- `config.yml`：过滤选项、处理动作、提示文本、域名白名单与 GeoIP 设置
- `message-blacklist.yml`：消息违禁词/正则列表
- `ip-whitelist.yml`：允许出现在消息或命令中的 IP；未列出的 IP 默认全部拦截

从旧版本升级时，原 `config.yml` 中的 `banned-words` 和 `ip-domain-detection.ip-whitelist`
会在首次加载时自动合并到两个新文件中；旧的 CIDR/IP 黑名单配置会被移除。

`/bwf geoip update` 默认从 [P3TERX/GeoLite.mmdb](https://github.com/P3TERX/GeoLite.mmdb) 下载 `GeoLite2-Country.mmdb`，保存到插件数据目录并自动重载。GeoLite2 数据库由 MaxMind 提供，请遵守其许可要求。

GeoIP 只用于玩家登录 IP 的国家/地区限制。它适合遭遇特定地区攻击、或需要国家白名单/黑名单的服务器；如果你只是要拦截聊天里的 IP/域名广告，可以保持 `geoip-enabled: false`。

拼音模糊匹配默认开启。旧版本已经生成过配置文件的服务器，需要确认 `fuzzy-pinyin.enabled` 是 `true`。
