# 0.9.1 手机访问与电脑会话

- Codex / DSH 合并到同一个「手机访问」入口，顶部直接切换，保留各自登录和网页状态，重新打开记住上次选择。
- Codex 增加「电脑会话」：列出电脑本机历史的标题与目录，支持搜索，选中后载入 HAPI，可继续原生线程。
- Windows 脚本增加 `Configure -AllDrives`，显示所有当前可用挂载盘，兼容旧的单目录配置；更新范围无需重新安装，保留端口、令牌和会话库。

已运行 0.9.0 的电脑端，下载本 Release 的新版 `codex-remote.ps1`，执行：

```powershell
& .\codex-remote.ps1 -Action Configure -Root 你的原安装目录 -AllDrives
& .\codex-remote.ps1 -Action Start -Root 你的原安装目录
```

[完整连接说明](https://github.com/thdlrt/AndroidToolbox/blob/v0.9.1/docs/CODEX-REMOTE.md)。本次开发机已完成范围升级，无需再执行。

验证：112 项 JVM 测试（0 失败、1 项既有跳过）、30 项 Android 15 模拟器验收及 Windows 配置迁移通过；真实 HAPI＋官方 Codex 验证了双目录浏览、本机历史发现、载入及同一原生线程续聊。本机已实测读取 27 个历史会话。完整 lint 的 19 项错误均与 0.9.0 已发布源码一致，无新增错误。

本版可载入 HAPI 支持的本机未归档历史；不实时接管桌面正在运行的轮次，不自动发送消息。外网仍需其他系统 VPN/Tailscale，工具箱自带回家 VPN 排除自身。实体手机、外网及网页语音未验收。
