# 安卓工具箱基座

本项目从 LanBridgeAndroid 迁移。参考 WinToolbox 的工具首页、常用工具、功能页、设置和后台任务结构，使用原生 Android Java 实现，不引入桌面端运行时。

- `MainActivity`：首页、工具目录、设置与更新状态。页面变化不持有或销毁 VPN。
- `ToolRegistry`：工具 ID、名称、说明、Activity 的唯一登记入口。
- `VpnActivity`：原回家 VPN 表单、状态和凭据操作。
- `BridgeVpnService`：前台 VPN 服务、连接生命周期、认证续期。
- `NasSession`、`WsChannel`、`SocksBridge`：认证、WebSocket 隧道和本地 SOCKS5。
- `LoginStore`：Android Keystore 绑定本机的密码加密存储。
- `AppUpdater`：应用级后台下载任务；独立 HTTP 客户端，不携带 NAS Cookie。
- `ReleaseInfo`：正式版语义版本比较、官方资产路径校验。
- `UpdateProvider`：仅通过临时读取授权向安装器提供一个 APK。

新工具需实现自己的 Activity、在 Manifest 注册，再加入 ToolRegistry。保持权限按需申请，不为尚未实现的工具添加空入口。

## 迁移与数据

沿用 `net.lanbridge.android` 和历史发行签名，使 0.1.x 可覆盖升级到工具箱。原 `profile` 配置及 `lanbridge-login-v1` Keystore 别名不变。新安装不会预填私人地址或账号。

原目录保留作为迁移前快照。当前开发机 `.build` 使用 E 盘原工具链目录的目录联接；这只是本机缓存，不纳入 Git。新克隆可以运行 bootstrap 建立自己的 `.build`，源码不依赖旧项目。

## 更新信任与限制

只查询 `thdlrt/AndroidToolbox` 最新正式 Release，必须有 APK 与 SHA-256。下载后检查哈希、包名、递增版本号、版本名及与当前应用一致的签名证书。错误保留当前安装；不绕过 Android 安装权限或确认。

SHA-256 提供传输完整性，发布方身份最终由 APK 签名约束。签名密钥只保存在本机 `.signing`，不上传到仓库或 GitHub Actions。

首版仅实现回家 VPN，仍是 IPv4 TCP 转发；普通 UDP、QUIC、ICMP、IPv6 不支持。工具箱迁移不声称解决 FN Connect 中继链路性能上限。
