# Codex 远程（Codex WebUI）

0.9.3 的「手机访问 → Codex」嵌入 [DusuWorks/codex-webui](https://github.com/DusuWorks/codex-webui)。网页与 Windows Codex Desktop 共用一个 app-server：桌面发起的会话、实时输出、引导和队列都走同一运行时。DSH 仍在同一个入口；HAPI 使用独立连接和凭据，保留用于它自己管理的会话。

## Windows 安装

需要 Node.js 20+、Git、PowerShell 7，以及已经登录或配置好 provider 的 Codex Desktop。无需额外 OpenAI API Key。下载 Release 中的 `setup-codex-webui.ps1` 和 `codex-webui-lan.patch`，放在同一目录：

```powershell
pwsh -File .\setup-codex-webui.ps1 -Action Install -Root E:\Tools\CodexWebUI
```

安装固定上游提交 `269d797196edfd1db134d12c4dda2d9f716cc1db`，应用局域网 HTTP 兼容修复并运行测试，然后安装桌面启动器。重复安装会保留令牌、上传文件和日志；有冲突的本地源码改动会明确报错。源码位置是 `-Root`，运行数据默认在 `%LOCALAPPDATA%\CodexWebUI`。安装不会替你在 Codex 侧栏登记项目。

准备好重启 Codex Desktop 时执行：

```powershell
pwsh -File .\setup-codex-webui.ps1 -Action Enable -Root E:\Tools\CodexWebUI
```

此操作将用户级 `CODEX_CLI_PATH` 指向桌面桥接启动器并保存原值。**完全退出再重新打开 Codex Desktop** 后生效；脚本不会主动退出应用。新启动器让桌面和网页共用真正的 app-server，不修改 AppX 文件或用户 Codex 配置，也不另开引擎抢占正在运行的会话。桌面启动后自动启动一份网页服务，默认端口 9526；保持桌面应用运行。

```powershell
pwsh -File .\setup-codex-webui.ps1 -Action Status -Root E:\Tools\CodexWebUI
pwsh -File .\setup-codex-webui.ps1 -Action QR -Root E:\Tools\CodexWebUI
```

QR 命令在电脑本地显示私有连接链接和二维码。链接含令牌，不要贴进群聊或问题单。普通 Windows 防火墙提示只允许受信任网络；手机需要能访问电脑的 9526 端口。同一局域网直连，外出使用已有系统 VPN/Tailscale 或自行配置的 HTTPS 私有入口。工具箱自带回家 VPN 排除工具箱自身。

## 安卓使用

1. 打开「全部工具 → 手机访问 → Codex」。
2. 填写 WebUI 地址，例如 `http://192.168.1.10:9526` 和访问令牌；也可粘贴带 `?token=` 或 `#token=` 的完整连接链接。应用分离令牌后加载原地址，不再自动增加端口。
3. 直接从会话侧栏选择桌面会话，活跃状态会同步更新。消息实时流入，支持 Markdown 标题、列表、表格和代码块；向上阅读时不强制拖回底部。
4. 回复进行中点击发送，可选择「立即引导」「加入队列」或「停止当前回复」。引导针对当前 turn；队列由共享运行时保存并接续执行。
5. 输入栏齿轮展开模型、推理强度、权限设置。模型和强度写入共享会话，活跃轮次期间的变更用于后续轮次；实际可选项以本机 provider 返回为准。
6. 新会话可浏览当前 Windows 账号可访问的磁盘与目录。默认令牌不限定单个工作区；无权访问的目录仍会报错。

顶部可切换 DSH、HAPI、全屏和连接设置。DSH、Codex WebUI、HAPI 的凭据分开保存；旧 HAPI 地址和令牌不会自动用于新服务。网页图片上传使用系统文件选择器。没有接入网页语音录制。

## 回退

```powershell
pwsh -File .\setup-codex-webui.ps1 -Action Disable -Root E:\Tools\CodexWebUI
```

恢复原用户级 `CODEX_CLI_PATH`，再自行重启桌面。不会删除会话、令牌或源码。旧版 HAPI 服务仍可按 [0.9.2 的安装说明](https://github.com/thdlrt/AndroidToolbox/blob/v0.9.2/docs/CODEX-REMOTE.md)独立使用；3007 自制快照页面已不作为默认方案。

## 验证与边界

- 上游测试加局域网 UUID 兼容回归共 89 项通过。
- 真实 Windows Codex 引擎、隔离 CODEX_HOME 和模拟 Responses 服务：14 项共享运行时检查通过，覆盖双向会话发现、流式事件、引导进入模型输入、队列接续、模型/强度同步、停止和重连恢复。模拟的是模型响应，Codex 引擎与桥接使用真实二进制。
- Android 15 指定模拟器：26 项 WebView 检查通过，覆盖手机渲染、发送、引导、排队、模型/推理强度实际进入模型请求、停止、凭据隔离、重载、全屏和软键盘。
- 安装脚本已在现有独立源码目录重复执行并验证，未擅自重启正在使用的桌面应用。
- 本机真实 Codex Desktop 在启用后完整重启，已验证共享管道连接、活跃桌面会话发现及本轮新增消息同步；网页读回当前模型和推理强度，412px 手机视口输入栏可见且无横向溢出。启用前已经运行的桌面进程仍须完整重启才能接入。
- 本地 Windows 桌面运行时是本版范围；其他远程主机自己的 app-server、ChatGPT 云会话、实体手机及外网链路不在上述验收范围内。

复现：

```powershell
node scripts/test-codex-webui.mjs E:\Tools\CodexWebUI C:\实际路径\codex.exe
```

加 `--serve` 保留隔离服务，供明确选定的模拟器运行 `CodexWebInstrumentation`。所有测试使用临时配置，不修改用户 Codex 账号和配置文件。上游为 MIT 许可，本项目提供固定版本安装和小型兼容补丁；[上游源码与许可](https://github.com/DusuWorks/codex-webui/blob/main/LICENSE)。
