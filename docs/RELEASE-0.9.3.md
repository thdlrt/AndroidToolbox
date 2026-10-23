# 0.9.3 Codex WebUI 共享桌面运行时

「手机访问 → Codex」改用开源 Codex WebUI，支持实时流式输出、立即引导、原生队列、停止、模型及推理强度切换。DSH 保持同一入口，原 HAPI 登录独立保留。

电脑端改为一次安装桌面桥接，完全退出并重新打开 Codex Desktop 后，共用同一个 app-server。修复上游普通局域网 HTTP 缺少 randomUUID 导致无法发送的问题，改善 Markdown 表格显示；手机键盘、全屏、窄屏与宽屏已检查。

下载 setup-codex-webui.ps1 与 codex-webui-lan.patch 到同一目录，在 PowerShell 7 执行：

```powershell
pwsh -File .\setup-codex-webui.ps1 -Action Install -Root E:\Tools\CodexWebUI
# 准备好重启桌面时执行
pwsh -File .\setup-codex-webui.ps1 -Action Enable -Root E:\Tools\CodexWebUI
# 手动完全退出并重开桌面后，显示私有连接二维码
pwsh -File .\setup-codex-webui.ps1 -Action QR -Root E:\Tools\CodexWebUI
```

安卓填写新的 WebUI 地址（默认 9526）及令牌，不再使用原 HAPI 的相邻端口。旧 HAPI 凭据保留，不能用它连接新服务。

验证：119 项 JVM 测试（0 失败、1 项既有跳过）；89 项 WebUI 测试；真实 Codex 二进制与原生 C# 启动器的 14 项共享引擎检查；26 项安卓 WebUI 检查；30 项 HAPI/DSH 安卓回归。覆盖流式中间态、引导进入模型输入、队列接续、模型/强度实际进入请求、停止、重载、系统附件选择器、凭据隔离与键盘。模型端使用隔离 Responses 模拟服务，未修改真实用户配置或向其他真实聊天发消息。

当前真实桌面接入仍待启用和重启后的最终验收；本地 APK 已构建，尚未公开发布。实体手机、外网、网页语音及其他主机的运行时未验收。

[完整连接、升级与回退说明](https://github.com/thdlrt/AndroidToolbox/blob/v0.9.3/docs/CODEX-REMOTE.md)。APK 沿用历史包名和签名，可覆盖升级并保留数据。