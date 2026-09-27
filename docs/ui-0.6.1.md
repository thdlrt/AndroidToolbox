# Android 0.6.1：界面与窗口适配验收

## 改动

- 首页按可用宽度显示一至三列卡片；VPN、中转、记账、网络诊断、取件使用不同图标和颜色。
- 600dp 以下使用底部导航；600–1099dp 使用 88dp 紧凑侧栏；1100dp 起使用 200dp 侧栏。侧栏可滚动，横屏仍可访问全部工具。
- AI、取件、WebDAV、诊断和 VPN 按内容宽度切换单栏/双栏。内容最大宽度 1120dp。
- 统一页面标题、卡片、按钮间距，修正记账与诊断中把像素当作 dp 的边距。
- 折叠尺寸变化不重建表单；保留输入草稿。放大字体时减少网格列数；中转按钮随文字高度增长。

## 验证（2026-09-27）

在明确指定的 SDK 模拟器 emulator-5560 上运行，未操作用户手机。

| 场景 | 尺寸 / 密度 | 结果 |
| --- | --- | --- |
| 普通手机 | 1080×1920 / 420dpi，约 411dp | 169 项布局断言通过 |
| 展开屏及实时收窄 | 1800×1800 → 900×1800 / 360dpi | 188 项断言通过，表单对象与草稿保留 |
| 窄屏、大字体 | 900×1600 / 400dpi，360dp，字体 1.3 | 169 项断言通过 |
| 横屏 | 1920×1080 / 320dpi | 169 项断言通过 |
| 大屏完整侧栏 | 2400×1600 / 320dpi，1200dp | 169 项断言通过 |

每种布局检查首页、工具目录、设置、取件、AI、记账、诊断、WebDAV、VPN、中转共十页，验证网格边界、重叠、按钮文字高度及草稿保留，并输出截图。

- NavigationInstrumentation：190 项断言，包含 30 次页面切换、同一窗口/侧栏、草稿及返回路径。
- ParcelUiInstrumentation：33 项断言，日期快捷范围、独立供应商、密钥保护、主动开启及返回路径。
- 单元测试：49 通过，1 项原有跳过，0 失败。
- Debug、测试 APK 和 Release APK 构建通过；APK 包名、版本和原签名校验通过。

模拟窗口覆盖宽度变化，不代表已经验证真实折叠屏铰链遮挡、厂商多屏切换或不同屏幕密度之间的迁移。

## 重跑

先运行 `./build.ps1 -DebugOnly`，然后加载 `./env.ps1` 并运行 Gradle `:app:assembleDebugAndroidTest`。只安装到明确选定的模拟器，使用 `adb -s emulator-5560` 设置上表窗口尺寸和密度，再执行：

```
shell am instrument -w -e mode phone net.lanbridge.android.debug.test/net.lanbridge.android.AdaptiveUiInstrumentation
```

`mode=fold` 会在检查结束前把模拟窗口收窄到 900×1800，检验视图和草稿保留。其余 mode 用于截图文件名前缀。截图写入调试应用 external files 目录。测试后恢复 `wm size reset`、`wm density reset` 和原字体大小。

本地签名产物：`outputs/AndroidToolbox-0.6.1.apk`。版本代码 12，包名 `net.lanbridge.android`。

SHA-256：`45439620acdb675e0ccde29870c13a1f492d7933eb7960d32ac386c802687905`。
