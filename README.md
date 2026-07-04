# SimpChatCensor

SimpChatCensor 是一个面向 Paper 1.21.4+ / Folia 26.1.2+ 的聊天与命令违禁内容过滤插件。

## 功能

- 普通关键词与正则匹配
- Unicode 规范化、变音剥离、Leet 还原、空白移除
- 拼音模糊匹配
- 编辑距离模糊匹配
- IPv4、IPv4 混淆分隔符、IPv6、域名、CIDR 与私网 IP 检测
- 可选 GeoIP 国家/地区拦截
- 支持 `cancel`、`filter`、`warn`、`kick` 处理动作
- Folia 调度兼容

## 构建

```powershell
mvn clean package
```

构建产物位于 `target/SimpChatCensor-1.0-beta.jar`。

## 自动发布

推送 `v*` 标签会触发 GitHub Actions 自动构建并创建 GitHub Release。

```powershell
git tag -a v1.0-beta -m "SimpChatCensor 1.0-beta"
git push origin v1.0-beta
```

标签中包含 `alpha`、`beta` 或 `rc` 时，Release 会自动标记为预发布。

## 命令

```text
/bwf reload
/bwf add <词/IP/域名>
/bwf remove <词/IP/域名>
/bwf list
/bwf geoip reload
/bwf whitelist add <IP/域名>
```

## 作者

主作者：GPT-5.5

全部作者：Minecraft0122, SimpFun, GPT-5.5
