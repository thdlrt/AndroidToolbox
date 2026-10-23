# Codex 远程（实时桌面 / HAPI）

安卓工具箱的「手机访问」在同一个入口切换 Codex / DSH，各自保留登录和页面。0.9.2 起 Codex 默认直接显示电脑桌面会话与运行状态，无需搜索或导入；点开后自动更新输出，可向同一桌面会话发消息。顶部 HAPI 切换至原网页，用于它独立管理的会话。

## Windows 电脑

从本次 Release 同时下载 `codex-remote.ps1` 和 `codex-desktop.mjs`，放在同一目录，需要已有 Node.js/npm，以及已登录的 Codex 账号或已配置的 provider。脚本安装独立的 HAPI 0.30.7 和 Codex CLI 0.164.0，不替换桌面应用、不改用户的 Codex 配置文件。

在 PowerShell 执行，按实际位置修改路径：

```powershell
& .\codex-remote.ps1 -Action Install -Root E:\Tools\CodexRemote -AllDrives -ListenHost 0.0.0.0
& .\codex-remote.ps1 -Action Start -Root E:\Tools\CodexRemote
& .\codex-remote.ps1 -Action Info -Root E:\Tools\CodexRemote
```

`Install` 是明确的软件下载动作，使用大陆 npm 镜像并由 npm 校验完整性；镜像不可用时可按错误提示使用官方源。`AllDrives` 在 runner 启动时列出当前 Windows 账号可访问的已挂载磁盘，包括可用的映射盘；新安装未指定目录时也采用此模式。可选 `-WorkspaceRoot C:\work,E:\code` 限定一个或多个目录。Codex 文件和命令权限仍由会话自身设置决定，浏览范围不是操作系统沙箱。

从 0.9.0 / 0.9.1 升级电脑脚本时，保留原安装目录的 data；无需重复安装 npm 包：

```powershell
& .\codex-remote.ps1 -Action Configure -Root E:\Tools\CodexRemote -AllDrives
& .\codex-remote.ps1 -Action Start -Root E:\Tools\CodexRemote
```

Configure 保留端口、监听地址、凭据和会话库；Start 刷新 runner 的磁盘范围，已有 hub 持续运行。普通用户无权读取的目录仍由 Windows 拒绝访问。

`Info` 只在电脑本地显示访问令牌。不要把令牌发到群聊、问题单或日志。HAPI 默认端口 3006，实时桌面端口为 3007（原端口加一），共用原令牌；安卓端仍填写原地址，自动连接下一端口。两个端口均需可达。使用 HTTPS 时需要在下一端口提供对应 HTTPS 反向代理；目前不支持单域名路径代理。安卓端填写电脑可达 IP，例如 `http://192.168.1.10:3006`，不要填写 `127.0.0.1`。首次连接若被 Windows 防火墙拦截，仅允许受信任的专用网络访问；不要把这个服务端口直接转发到公网。

默认不启用公共 relay、远程推送或开机自启。`Start` 启动 HAPI hub、runner 和桌面 companion；更新 companion 只重启本服务，不重启桌面 Codex 或已有 HAPI hub。服务使用 Windows WMI 从独立进程启动，关闭启动终端或 Codex 聊天不会关闭服务。

```powershell
& .\codex-remote.ps1 -Action Status -Root E:\Tools\CodexRemote
& .\codex-remote.ps1 -Action Stop -Root E:\Tools\CodexRemote
```

配置、令牌、日志和 HAPI 会话库在安装目录的 `data`，目录权限只授予本机当前账号和 SYSTEM。卸载或更新前自行保留该目录。脚本重复 Install 会保留已有令牌与会话，但会更新连接参数；已有服务应先 Stop 再更新。

## 安卓

1. 打开「全部工具 → 手机访问 → Codex」。下次打开会记住选择；顶部可随时切换 DSH。
2. 填入电脑地址和 `Info` 显示的访问令牌，点「连接」。也可粘贴 HAPI 的 `?token=` 链接，应用会分离令牌，网页地址不携带令牌。
3. 默认直接列出桌面最近 50 个会话及置顶，运行中与待处理排在前面。直接点会话查看当前消息与工具输出，3 秒自动更新；筛选和搜索均可选。窄屏返回列表后可选择其他会话，宽屏左右两栏。
4. 输入消息并发送，会经桌面原有连接到达同一会话；不会另建 app-server 或导入副本。活跃会话如何处理后续输入由桌面决定。发送失败或超时需先检查会话；不会自动重发。
5. 需要创建独立 HAPI 会话时点顶部 HAPI，选择电脑与目录；其提问、审批、停止和附件沿用 HAPI 自己的功能。点「实时会话」回到桌面列表。

右上角提供刷新、全屏和连接设置。返回键先退出全屏，再按网页历史返回；切换工具保留 WebView。附件使用安卓系统文件选择器。没有额外的麦克风、相机或文件全盘访问权限；网页语音录制未接入。

访问令牌通过独立的 Android Keystore 存储；HAPI 自身网页登录数据保存在应用私有 WebView 存储。换服务器时不复用旧令牌；「清除登录」清除本入口的凭据和对应服务网页存储，不清除 DSH 的 cookie。

## 范围

- 实时桌面使用已安装 Codex Desktop 的 app-tools 连接；必须保持桌面应用打开，至少打开一个已有会话。可显示该接口返回的本机／远程 Codex 最近会话，归档列表不在本版范围。该接口按轮次提供消息快照，长轮次显示最近 100 项工具／消息，可加载更早轮次；并非逐 token 推流，超长工具输出有截断标志。
- 桌面接口目前没有停止、审批、原生交互选项答复能力，这些操作需在电脑端处理；可输入补充文字继续同一会话，但不等于回答待审批请求。HAPI 功能只适用于 HAPI 自己管理的运行时。
- Companion 只提供会话列表、读取和发送三类受令牌保护的接口，不代理任意 MCP 调用；不修改用户 Codex 配置，不读取 provider 密钥。安装目录内的 Node 服务使用本机已有桌面 app-tools，桌面版本变化可能使接口不可用。
- 同一局域网可直连；外出可用其他系统 VPN、Tailscale 或自行部署的 HTTPS 私有入口。工具箱自带「回家 VPN」排除工具箱自身，不能为此 WebView 提供访问路径。
- 保持电脑在线且 HAPI hub、runner 运行。电脑睡眠或服务停止时不可达；网页会处理会话重连，主页面连接失败可刷新重试。
- HAPI 为 AGPL-3.0-only 开源项目；本项目不打包或修改 HAPI 代码，Windows 脚本按固定版本独立安装。源代码：<https://github.com/tiann/hapi>。Codex 使用账号或 provider 自身额度。

## 验证

- Windows x64：固定版本的真实 HAPI hub、runner 与官方 Codex 0.164.0，隔离 CODEX_HOME、模拟 Responses 服务，覆盖创建会话、消息往返、request_user_input、回答送达与同一原生线程历史。
- Android 15 模拟器：30 项检查，覆盖统一入口、DSH 表单登录、记住后端、两个 WebView 保留、电脑会话搜索与历史载入，以及真实 HAPI 网页登录、地址中无令牌、Keystore 存取、系统文件选择器 content URI 回传、全屏、折叠尺寸变化、返回和跨源令牌隔离。
- 本机实际 provider：HAPI 新会话由 `gpt-6.1-sol` 返回「连接正常」。这只证明本轮账号与模型的调用，不代表其他账号或模型均可用。
- 0.9.1：真实双目录 runner 浏览、发现原生 CLI 历史、载入并继续相同原生线程通过。本机 runner 实测显示 C:/D:/E: 等 7 个挂载盘，可浏览 C:，发现 27 个本机历史会话。
- 实体安卓手机、外网 VPN、网页语音未做验收。

复现协议测试：

```powershell
python scripts/test-codex-remote.py --host-root E:\Tools\CodexRemote
```

加 `--serve` 会保留隔离 fixture，供明确选定的模拟器运行 `HapiInstrumentation`。测试不改用户 Codex 配置。

静态检查：112 项 JVM 测试，0 失败、1 项既有 NAS 实机测试默认跳过；Windows 配置迁移测试通过。完整 lint 扫描仍有 19 项既有错误，逐行核对 0.9.0 已发布源码均存在，无新增错误；未为本功能修改这些既有问题。

## 0.9.2 验证

114 项 JVM 测试（0 失败、1 项既有 NAS 跳过）、8 项 Node API 测试和17 项 Android 15实时页面检查通过。生产桌面只读验证返回25个会话，当前会话active、轮次inProgress；消息发送、超时和重复提交均用隔离fixture验证，未向其他真实聊天发测试消息。

```powershell
node --test scripts/test-codex-desktop.mjs
python scripts/test-codex-remote-config.py
```

实体手机、外网连接、网页语音仍未验收。

回归补充：真实隔离 HAPI＋官方 Codex 协议链路通过；另30项 Android HAPI／DSH回归检查通过（登录、原生历史载入、附件、两个后端保留、全屏和折叠）。
