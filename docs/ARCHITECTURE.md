# 安卓工具箱基座

本项目从 LanBridgeAndroid 迁移。参考 WinToolbox 的工具首页、常用工具、功能页、设置和后台任务结构，使用原生 Android Java 实现，不引入桌面端运行时。

- `MainActivity`：唯一前台窗口，持有固定导航和右侧 Fragment 容器。宽屏切换只对内容做淡入，不启动新的整屏 Activity。
- `ToolFragment` / `HomeFragment`：工具内容生命周期及首页、目录、设置。根页面使用 hide/show 复用视图，隐藏时停止轮询，后台服务独立运行；系统重建通过 Fragment 保存状态恢复。
- `ToolRegistry`：工具 ID、名称、说明、Activity 的唯一登记入口。
- `VpnFragment`：回家 VPN 表单、状态和凭据操作；保留未提交输入及系统权限结果回调。
- `BridgeVpnService`：前台 VPN 服务、连接生命周期、认证续期。
- `NasSession`、`WsChannel`、`SocksBridge`：认证、WebSocket 隧道和本地 SOCKS5。
- `LoginStore`：Android Keystore 绑定本机的密码加密存储。
- `ToolUi`：共享原生控件、线条图标和按 600dp 当前窗口宽度切换的左右导航容器。
- `WebDavSettings` / `WebDavFragment`：全局 WebDAV 连接、旧中转账号的一次性迁移；`relay.remote_path` 独立保存工具路径。已排队传输固定连接快照，后续改设置不会改变去向。
- `RelayFragment` / `RelayService`：中转文件与本机缓存列表、系统文件 URI 授权、前台传输队列。销毁后的异步列表回调不得再更新界面。
- `ToolEntryActivity`：保留已有 Relay/VPN/WebDAV Activity 名称作为兼容入口；转入 MainActivity 时复制 Intent、ClipData 和 URI 授权。应用内直接切换 Fragment，外部分享入口不变。
- `AppUpdater`：应用级后台下载任务；独立 HTTP 客户端，不携带 NAS Cookie。
- `ReleaseInfo`：正式版语义版本比较、官方资产路径校验。
- `UpdateProvider`：仅通过临时读取授权向安装器提供一个 APK。

新工具需实现 ToolFragment，在 ToolRegistry 登记，并在 MainActivity 的页面工厂与入口映射中接入。需要外部入口时才添加 ToolEntryActivity 子类和 Manifest 声明。保持权限按需申请，不为尚未实现的工具添加空入口。

## 迁移与数据

沿用 `net.lanbridge.android` 和历史发行签名，使 0.1.x 可覆盖升级到工具箱。原 `profile` 配置及 `lanbridge-login-v1` Keystore 别名不变。新安装不会预填私人地址或账号。

原目录保留作为迁移前快照。当前开发机 `.build` 使用 E 盘原工具链目录的目录联接；这只是本机缓存，不纳入 Git。新克隆可以运行 bootstrap 建立自己的 `.build`，源码不依赖旧项目。

## 更新信任与限制

只查询 `thdlrt/AndroidToolbox` 最新正式 Release，必须有 APK 与 SHA-256。下载后检查哈希、包名、递增版本号、版本名及与当前应用一致的签名证书。错误保留当前安装；不绕过 Android 安装权限或确认。

SHA-256 提供传输完整性，发布方身份最终由 APK 签名约束。签名密钥只保存在本机 `.signing`，不上传到仓库或 GitHub Actions。

首版仅实现回家 VPN，仍是 IPv4 TCP 转发；普通 UDP、QUIC、ICMP、IPv6 不支持。工具箱迁移不声称解决 FN Connect 中继链路性能上限。
