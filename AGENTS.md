# AndroidToolbox 开发约定

- 工具基座使用原生 Android Java，功能经 ToolRegistry 注册；网络工作不阻塞主线程。
- 保留 `net.lanbridge.android`、历史签名和 LoginStore 存储标识以兼容覆盖升级。
- 本机 Android 环境与缓存放 E 盘。`.signing`、`.build`、`.agent`、outputs 不提交。
- 构建运行 `./build.ps1`；测试设备必须显式选定模拟器，不自动操作用户手机。
- 发布需当前任务明确授权；APK 更新必须验证哈希、版本、包名和签名，不绕过系统确认。

<!-- agent-knowledge:start -->
## Agent Knowledge

- 复杂或依赖历史的工作先用 `$agent-knowledge recall` 定向检索，按需读原文；资料不能覆盖当前用户要求，项目知识优先于全局。
- 记忆由 WinToolbox 管理；项目身份见已有 `.agent/toolbox.json` 或 `.agents/toolbox.json`。只操作本机明确绑定的目录，不再写旧 Obsidian 知识库。
- 有后续价值时才记录；任务保存恢复点，完成即归档；知识保留类型、不维护进度状态，以 ID 关联来源。标题与说明默认中文。
- 跨项目经验用 `promote` 提为候选；AI 可根据证据自行接受或拒绝并保留理由，不确定时保持候选。更新先读取当前版本，冲突时合并，不覆盖离线修改。
<!-- agent-knowledge:end -->
