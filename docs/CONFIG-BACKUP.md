# 统一配置备份 v2

新文件位于 `config-backups/shared/config-{windows|android}-YYYYMMDD-HHmmss-32hex.wtconfig.json`，不可覆盖既有文件。

明文顶层严格字段：`format=wintoolbox-config-backup`、`version=2`、`platform`、`created_at`（Unix 秒）、`config`、`ai`。Android config 沿用 AndroidConfigBackup schema 1；Windows config 只读取为文件名到对象的映射，不应用到手机。ai 沿用 wintoolbox-ai-config v1。

密文顶层严格字段：`format=wintoolbox-config-backup-encrypted`、`version=2`、`salt`、`nonce`、`ciphertext`。后三者为规范 Base64。以当前 WebDAV 密码通过 PBKDF2-HMAC-SHA256 200000 次派生 32 字节密钥；salt 16 字节，nonce 12 字节；AES-256-GCM，AAD 为 UTF-8 `wintoolbox-config-backup-v2`。GCM 标签附在 ciphertext 后。密文文件不超过 4 MiB。

恢复先验证并预览，确认时核对配置与连接状态。Android 备份恢复首页常用项、NAS 地址/用户名/模式及 AI；Windows 备份只恢复 AI。当前 WebDAV、NAS 密码、取件开关和业务数据不从备份覆盖。NAS 账号变化时清除旧密码。AI 缺少手机专属 parcel / parcel_vision 时保留本机角色；供应商 ID 碰撞且配置或密钥不同则复制本机供应商并改写角色引用。

恢复副本以同协议加密保存。正在恢复的原始偏好通过设备 Keystore 加密事务记录暂存，失败立即回滚，进程中断后下次启动回滚；不会把明文 API Key 写入恢复文件。

旧 `config-backups/android/android-config-*.json` 和 `ai-config/config-v1.json` 保持兼容列表与恢复。新写入均为共享 v2。更改 WebDAV 密码后，旧加密备份仍需要旧密码；请在保留配置的设备重新备份。

JVM 固定向量：`app/src/test/resources/config_snapshot_v2.json` 为 Windows 生成；测试输出 `app/build/config_snapshot_android_v2.json` 可交给 Windows 解密验证。模拟器 `NavigationInstrumentation -e action config_backup_test` 检查跨端/本端恢复、冲突角色、预览过期、提交失败回滚、重启恢复和旧格式。
