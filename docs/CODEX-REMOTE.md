# Codex 远程（HAPI）

安卓工具箱的「Codex 远程」打开电脑上运行的 HAPI 网页端。电脑运行官方 Codex，手机负责发消息、查看输出、回答提问和审批。现有 DSH「手机访问」保留。

## Windows 电脑

从本次 Release 下载 `codex-remote.ps1`，需要已有 Node.js/npm，以及已登录的 Codex 账号或已配置的 provider。脚本安装独立的 HAPI 0.30.7 和 Codex CLI 0.164.0，不替换桌面应用、不改用户的 Codex 配置文件。

在 PowerShell 执行，按实际位置修改路径：

```powershell
& .\codex-remote.ps1 -Action Install -Root E:\Tools\CodexRemote -WorkspaceRoot E:\code -ListenHost 0.0.0.0
& .\codex-remote.ps1 -Action Start -Root E:\Tools\CodexRemote
& .\codex-remote.ps1 -Action Info -Root E:\Tools\CodexRemote
```

`Install` 是明确的软件下载动作，使用大陆 npm 镜像并由 npm 校验完整性；镜像不可用时可按错误提示使用官方源。`WorkspaceRoot` 限制网页浏览目录和创建会话的范围。Codex 本身的文件和命令权限仍由会话的权限设置决定，该目录不是操作系统沙箱。

`Info` 只在电脑本地显示访问令牌。不要把令牌发到群聊、问题单或日志。默认端口 3006；安卓端填写电脑可达 IP，例如 `http://192.168.1.10:3006`，不要填写 `127.0.0.1`。首次连接若被 Windows 防火墙拦截，仅允许受信任的专用网络访问；不要把这个服务端口直接转发到公网。

默认不启用公共 relay、远程推送或开机自启。`Start` 使用 Windows WMI 从独立进程启动，关闭启动终端或 Codex 聊天不会关闭服务。

```powershell
& .\codex-remote.ps1 -Action Status -Root E:\Tools\CodexRemote
& .\codex-remote.ps1 -Action Stop -Root E:\Tools\CodexRemote
```

配置、令牌、日志和 HAPI 会话库在安装目录的 `data`，目录权限只授予本机当前账号和 SYSTEM。卸载或更新前自行保留该目录。脚本重复 Install 会保留已有令牌与会话，但会更新连接参数；已有服务应先 Stop 再更新。

## 安卓

1. 打开「全部工具 → Codex 远程」。
2. 填入电脑地址和 `Info` 显示的访问令牌，点「连接」。也可粘贴 HAPI 的 `?token=` 链接，应用会分离令牌，网页地址不携带令牌。
3. 在 HAPI 中选择电脑和目录，创建 Codex 会话。可发消息、查看历史、回答提问、审批和停止当前轮次。

右上角提供刷新、全屏和连接设置。返回键先退出全屏，再按网页历史返回；切换工具保留 WebView。附件使用安卓系统文件选择器。没有额外的麦克风、相机或文件全盘访问权限；网页语音录制未接入。

访问令牌通过独立的 Android Keystore 存储；HAPI 自身网页登录数据保存在应用私有 WebView 存储。换服务器时不复用旧令牌；「清除登录」清除本入口的凭据和对应服务网页存储，不清除 DSH 的 cookie。

## 范围

- 这是 HAPI 管理的 Codex 会话。没有接管官方桌面 App 已经运行的任意聊天。
- 同一局域网可直连；外出可用其他系统 VPN、Tailscale 或自行部署的 HTTPS 私有入口。工具箱自带「回家 VPN」排除工具箱自身，不能为此 WebView 提供访问路径。
- 保持电脑在线且 HAPI hub、runner 运行。电脑睡眠或服务停止时不可达；网页会处理会话重连，主页面连接失败可刷新重试。
- HAPI 为 AGPL-3.0-only 开源项目；本项目不打包或修改 HAPI 代码，Windows 脚本按固定版本独立安装。源代码：<https://github.com/tiann/hapi>。Codex 使用账号或 provider 自身额度。

## 验证

- Windows x64：固定版本的真实 HAPI hub、runner 与官方 Codex 0.164.0，隔离 CODEX_HOME、模拟 Responses 服务，覆盖创建会话、消息往返、request_user_input、回答送达与同一原生线程历史。
- Android 15 模拟器：16 项检查，覆盖真实 HAPI 网页登录与历史、地址中无令牌、Keystore 存取、系统文件选择器 content URI 回传、切换工具保留页面、折叠尺寸变化保留全屏、返回和跨源令牌隔离。
- 本机实际 provider：HAPI 新会话由 `gpt-6.1-sol` 返回「连接正常」。这只证明本轮账号与模型的调用，不代表其他账号或模型均可用。
- 实体安卓手机、外网 VPN、网页语音未做验收。

复现协议测试：

```powershell
python scripts/test-codex-remote.py --host-root E:\Tools\CodexRemote
```

加 `--serve` 会保留隔离 fixture，供明确选定的模拟器运行 `HapiInstrumentation`。测试不改用户 Codex 配置。

静态检查：109 项 JVM 测试，0 失败、1 项既有 NAS 实机测试默认跳过。完整 lint 扫描有 19 项既有错误（以基线源码逐行核对），无新增错误；未为本功能修改这些既有问题。
