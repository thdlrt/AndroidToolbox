# 开发与贡献

先阅读 README 和 AGENTS.md。功能改动附带可复现步骤以及与影响范围相称的测试。

工具通过 `ToolRegistry` 登记，UI 用独立 Activity，持续任务由 Service 或应用级后台任务持有；禁止在主线程进行网络或文件下载。首页与全部工具复用同一份工具登记，常用项按稳定工具 ID 保存。

不要提交设备地址、账号、密码、Cookie、私钥、签名材料或构建缓存。测试只使用夹具；真实 NAS 测试必须显式提供环境变量。

更新应同时提高 `versionCode` 与 `versionName`。官方发行版必须使用原有签名密钥；自行构建使用自己的签名，不能覆盖官方 APK。发布文件名为 `AndroidToolbox-X.Y.Z.apk` 及同名 `.sha256`，标签为 `vX.Y.Z`，发布正式 Release 后客户端即可发现。

项目代码使用 MIT；第三方各自许可保留在 `app/src/main/assets/licenses/`。
