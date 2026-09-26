# 发布验证

## 0.3.3 固定侧栏导航（本地签名包，未发布）

- 19 项单元/协议测试通过，1 项真实 NAS 测试跳过；签名构建及 lintVital 通过。versionCode 8，沿用历史证书，指定模拟器从 0.3.2 覆盖升级成功。
- 解决根因：原来每个工具启动独立 Activity，系统转场会移动整条侧栏；现在 MainActivity 持有唯一窗口和侧栏，各工具只切换右侧 Fragment。
- Android 15、800dp 方形窗口：原生 Instrumentation 完成 190 个断言，包括连续 30 次菜单切换，窗口 token、侧栏及其行视图始终复用、侧栏无位移/透明度动画、只显示一个内容页、VPN 草稿保留、窗口重建后恢复连接编辑草稿、返回原工具。
- 正式签名包 UI 验证手机/方形宽屏与运行时尺寸变化；中转目录和选择状态在折叠展开及切换 VPN 后仍保留。路径单字段、全局设置、紧凑 VPN、返回首页通过。
- 正式签名包的首页/工具目录、常用项隐藏与恢复、重启持久化、VPN 返回、设置与许可入口回归通过。
- 独立 UID 测试应用完成冷/热启动 VIEW、SEND、SEND_MULTIPLE、ClipData-only、去重、排队、离开前台上传；7 个合成文件逐字节一致且恰好 7 次 PUT。
- 新脚本 `scripts/smoke-adaptive-ui.py` 增加跨工具返回后的目录/选择验证。原生测试构建 `:app:assembleDebugAndroidTest`，在显式选定的宽屏模拟器运行 `am instrument -w net.lanbridge.android.debug.test/net.lanbridge.android.NavigationInstrumentation`。
- APK SHA-256：`64164d9f47efbc1c98a405c6eecc34ef19b992ed5b1ddddc1628fcac3b48cf23`。仅本地构建，未 push 或发布；未操作实体手机，未验证物理铰链/厂商多窗口策略。

## 0.3.2 界面改版（正式发布）

- 2026-09-26 发布正式 Release `v0.3.2`，公开 `/releases/latest` 返回该版本。重新下载公开 APK 和校验文件，SHA-256 与本地签名包一致：`0db8c113c36050e8f67f835927a051f0ef94d0efad386fc6184af8479f5c4bbd`。使用应用实际 `ReleaseInfo` 代码解析线上响应，0.3.1 正确识别更新、0.3.2 不重复提示。

- 19 项单元/协议测试通过，1 项真实 NAS 测试跳过；发行构建与 lintVital 通过。包名不变，versionCode 7、versionName 0.3.2，签名与 0.3.1 一致，可覆盖安装。
- 指定 Android 15 模拟器验证 411dp 手机宽度、800dp 方形展开窗口、运行时折叠/密度切换。中转目录、选择状态保留；手机底部导航与宽屏侧栏正确切换。主页常用项、VPN 返回、设置和许可入口通过。
- 旧中转账号迁移至全局 WebDAV，原路径保留；中转目录页面只含一个路径字段。更改全局连接后重新读目录，旧目录列表与清理预览失效；后台任务继续使用提交时的连接快照。
- 不同 UID 的测试应用验证冷/热启动 ACTION_VIEW、SEND、SEND_MULTIPLE、ClipData-only、去重、排队及离开前台继续上传，文件逐字节一致。
- 发行包点文件直接唤起系统打开选择器，另一测试应用读取 `welcome.txt` 得到完整内容。已下载列表删除本机缓存成功，远端文件保持不变。
- 统一导航/文件/操作图标、触摸区域、卡片、主按钮和选中态，修正底部文字对齐；通知使用单色图标，启动图标支持系统自适应与主题色。状态轮询只在文字变化时更新。
- 文件传输验收使用本机合成服务，不涉及真实 NAS 或实体手机；更新发现与公开下载另经线上验证。尚未验证实体折叠屏物理铰链与厂商特殊分屏策略。

## 0.3.1

- 19 项单元/协议测试通过，1 项真实 NAS 测试默认跳过；Android lint 无错误。
- 正式 APK 包名 `net.lanbridge.android`，versionCode 6，versionName 0.3.1。签名证书 SHA-256 `f299b831be45343d8f2fb1b6f2c6557075946f93ab099fd1b4d746f1fd2501d7` 与 0.2.1 一致；指定模拟器覆盖安装后连接设置仍可使用。
- Android 15 `emulator-5554`，不同 UID 的独立测试应用提供受授权的 content URI：冷启动打开文件自动上传、SEND/SEND_MULTIPLE/ClipData-only、重复 URI 去重、忙碌时再次分享排队、回到桌面后继续上传通过。文件内容逐字节一致，没有重复 PUT。
- 打开/分享下载缓存由另一应用实际读取；系统文件选择器保存到 Downloads 后内容一致。搜索、全选、批量远端删除、本机缓存删除通过，缓存删除保留远端文件。
- 测试使用本机合成 WebDAV 服务与测试账号；没有操作实体手机或使用个人 NAS 凭据。真实设备后台策略和实际网络仍可能不同。

## 0.3.0 本地文件中转版本（未发布）

- 17 项单元/协议测试通过，1 项真实 NAS 测试默认跳过；新增 6 项 WebDAV 测试。
- Android lint 无错误；正式 APK 的包名为 `net.lanbridge.android`，versionCode 5，versionName 0.3.0，签名与 0.2.1 一致，SHA-256 随 APK 输出。
- 明确选定 `emulator-5554` 覆盖升级成功；工具目录、常用项持久化、原 VPN 页面与设置回归通过。
- 本机 WebDAV 测试服务下，保存设置与连接测试、目录浏览、系统文件选择上传、下载到 Downloads 后逐字节比对、上传日志、系统分享接收与同名不覆盖、预览并清理超过一周的旧文件均通过。
- 未操作用户实体手机，未发布 GitHub；手机直连真实飞牛的网络条件仍需在实际设备验收。

## 0.2.1

环境：Windows E 盘工具链；Android 15 x86_64 测试模拟器。初次公开发行版本为 0.2.1。

- 单元与协议测试：11 项通过，真实 NAS 环境测试 1 项按默认配置跳过。
- Android lint：通过（无错误；保留版本更新提示及原有 API 建议）。
- 正式 APK：包含四种 ABI，apksigner 校验通过。
- 覆盖升级：测试模拟器已安装旧版 0.1.1，在不卸载应用的情况下成功安装工具箱 0.2.0 验证版，证明原签名与包名兼容。
- UI 冒烟测试：工具目录、隐藏/恢复首页常用项、重启后偏好保留、VPN 页面、返回工具箱与设置入口通过。脚本为 `scripts/smoke-android.py --serial emulator-5554`，只接受显式选定的测试模拟器。
- 开源前扫描：源码中不含原个人 FN ID、个人公网测试目标、口令模式或私钥。签名、缓存、APK、项目知识库身份文件均未纳入 Git。

- 真实应用内更新：模拟器上的 0.2.0 从 GitHub Release 发现 0.2.1，应用自行下载 APK/哈希并验证，首次安装来源授权返回后唤起系统更新确认；点击 Update 后显示 App installed，系统包管理器确认 `versionCode=4`、`versionName=0.2.1`。此步骤未使用 adb install 替代下载或安装流程。
- 更新后再次执行完整 UI 冒烟测试通过，设置页实时检查 GitHub 后显示“已是最新正式版 · 0.2.1”。
- 干净原生构建：使用原始发布压缩包重新解包，将归档内头文件链接物化为普通文件（Windows CI 不依赖符号链接权限），四种 ABI 均重新编译通过。

此轮没有重新测试真实 NAS 数据吞吐，也没有实体手机验收；不把工具箱升级视为 VPN 性能改进。

## 2026-09-26 · 0.4.0 项目记账与网络诊断

- `./build.ps1 -DebugOnly` 与 `./build.ps1` 通过：31 个 JVM 测试，30 通过，1 个真实 NAS 测试按原策略跳过；包含新增合并、并发、删除、附件、非法操作和 1,200 次历史链用例。
- `emulator-5560`（显式指定的 LanBridgeTest，未操作用户手机）安装最新调试 APK 和测试 APK。`NavigationInstrumentation -e action ui` 通过 12 项记账检查。
- `NavigationInstrumentation -e action external` 通过 3 项实际外部应用检查：启动 `com.android.camera2`，点击快门、确认照片，照片回传工具箱；再启动系统 `com.google.android.documentsui`，选取测试 PNG，文件回传工具箱。此流程未使用 intent 拦截。
- 正式 APK：`outputs/AndroidToolbox-0.4.0.apk`，包名 `net.lanbridge.android`，versionCode `9`，versionName `0.4.0`，minSdk `29`，targetSdk `35`。
- APK SHA-256：`c1a2f1a4f1a7c38fa4afb11308680262a1f3872695d8a46abf361b708606aa22`；大小 4,615,924 字节。APK v2 签名验证通过，签名证书 SHA-256 与 0.3.2 一致：`f299b831be45343d8f2fb1b6f2c6557075946f93ab099fd1b4d746f1fd2501d7`。
- 网络诊断明确提示工具箱自身流量被回家 VPN 排除，结果对应直连网络。真实设备相机兼容性和厂商后台保活仍取决于设备。
- Windows `scripts/smoke_ledger_android.py` 对最新调试 APK 和本机独立 WebDAV 执行真实跨端测试：项目/账目/诊断配置和附件 PC→Android，Android 图片→PC，离线同字段冲突、不同字段合并、显式解决后回传、删除墓碑、按项目日期导出，以及冷启动自动拉取全部通过（225 个 HTTP 请求）。
- Python/Java JVM 互通脚本通过金额、项目、并发合并、冲突解决、收入状态归一及删除与离线编辑竞争的逐字段比较。
- 正式 APK 在 `emulator-5560` 从已安装 0.3.3 覆盖升级至 0.4.0；升级前后 4 份 shared_prefs 文件 SHA-256 完全一致，启动及系统包信息正常。

## 2026-09-26 · 0.5.0 统一 WebDAV 与配置备份

- `./build.ps1 -DebugOnly` 与正式签名构建通过：35 个 JVM 测试，34 通过，1 个真实 NAS 测试按原策略跳过。
- `emulator-5560` 的 `NavigationInstrumentation -e action settings_ui` 通过 7 项检查：统一地址/账号/密码/根目录四字段，手动备份入口、自动同步状态；中转站无独立路径输入，记账直接打开统一设置。
- 实际合成 WebDAV 下 `action webdav` 基线通过 10 项检查：保留旧记账根目录、派生中转目录、迁移旧文件、配置上传/列举/下载/恢复，密码排除、恢复副本、当前连接和记账数据保留。
- `action webdav -e move_root <新根目录>` 额外覆盖旧根迁移，以及 A→B→A 后上传新文件、再次 A→B 的重复切换场景。迁移完成标记按任务代次保存，重复切换不会跳过新文件。
- 最终 APK `outputs/AndroidToolbox-0.5.0.apk`：包名 `net.lanbridge.android`，versionCode `10`，versionName `0.5.0`；4,632,308 字节，SHA-256 `4ce4c0abf22390163b29fb0f9e72382eac45c2c31da6af01bfb31efe19b1ef03`。v2 签名通过，与 0.4.0 同证书（SHA-256 `f299b831be45343d8f2fb1b6f2c6557075946f93ab099fd1b4d746f1fd2501d7`）。
- 最终签名源码的合成 WebDAV 检查：13 项通过，含 A→B→A→B 根目录往返后新增文件再次迁移；跨端完整验收共 469 次请求。
- 最终记账互通回归 8 类场景通过（276 次 HTTP 请求）：双向项目/账目/附件/诊断配置、并发冲突与合并、删除、PC 按项目日期导出、Android 冷启动自动同步。
- 模拟器正式包 0.4.0 → 0.5.0 覆盖安装成功，包名与签名不变，启动成功；未对升级前后私有配置做哈希对照。
