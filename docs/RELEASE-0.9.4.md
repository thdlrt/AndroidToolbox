# 0.9.4 手机访问简化

「手机访问」只保留 DSH 和 Codex，移除旧 HAPI 切换按钮、认证及电脑会话导入实现。Codex 继续使用开源 Codex WebUI，共用桌面运行时。

升级保留现有 DSH、Codex WebUI 地址和登录信息，电脑上的 Codex WebUI 无需重新安装或重启。修正默认入口及全屏恢复仍依赖旧 HAPI 配置的问题。

验证：116 项 JVM 测试，0 失败、1 项既有 NAS 测试跳过；40 项 Android 15 模拟器检查通过，覆盖两种后端登录及切换、旧入口移除、页面保留、凭据隔离、页面与全屏恢复、流式中间态、引导、队列、模型和推理强度、停止、重载及键盘。真实 Codex 二进制与 C# 桥接启动器的 14 项共享引擎检查通过。引擎测试使用隔离配置和模拟 Responses 服务；实体手机、外网未验收。

APK 沿用原包名和签名，可覆盖升级。已有 0.9.3 电脑端无需下载额外文件；新安装请使用[连接说明](https://github.com/thdlrt/AndroidToolbox/blob/v0.9.4/docs/CODEX-REMOTE.md)。
