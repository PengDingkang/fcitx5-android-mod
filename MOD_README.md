# Fcitx5 Android Mod

这是一个基于 [Fcitx5 for Android](https://github.com/fcitx5-android/fcitx5-android) 的非官方个人 mod 版。它用于验证更符合个人输入习惯的键盘布局、候选词触摸行为、AI 翻译和插件改动。

这个仓库不是上游官方发布渠道。普通用户如果只需要稳定的 Fcitx5 for Android，应优先使用上游 GitHub Release、F-Droid 或 Google Play 版本。

## 当前发布状态

目前还没有正式稳定版 release。

公开预览版从 `mod-v0.1.0 Preview` 开始，并在 GitHub Release 中标记为 pre-release。CI artifact 只作为开发测试构建，不等同于正式 release。

## 下载和安装

建议普通试用者只安装 release APK。

- 本体 APK：`org.fcitx.fcitx5.android-mod-vX.Y.Z-aN-arm64-v8a-release.apk`
- Anthy 插件 APK：`org.fcitx.fcitx5.android.plugin.anthy-mod-vX.Y.Z-aN-arm64-v8a-release.apk`，仅在该版本包含 Anthy 变化或发版时手动选择包含插件时上传。

CI build 会同时产出 debug 和 release artifact。debug/dev 构建只用于本地调试，不建议作为日常输入法长期使用。

release 文件名和应用内版本里的 `aN` 表示当前 mod 分支相对上游 `master` 多出的 commit 数，例如 `mod-v0.1.0-a15` 表示比上游 `master` 多 15 个 mod commit。Android 升级判断仍使用从 tag 推导的 `versionCode`，不依赖 `aN`。

## 签名和升级

mod 版使用自己的 release 签名，不兼容上游官方 APK 的签名。

这意味着：

- 不能直接覆盖安装上游 GitHub/F-Droid/Google Play 版本。
- 从上游版本迁移到 mod 版前，需要先导出配置和用户数据，再卸载上游版本并安装 mod 版。
- mod 版之后的正式 release 应继续使用同一套 release key；否则用户也无法直接升级。

如果同时安装插件，插件 APK 应使用 release notes 标记的兼容版本；不是每个本体 release 都会重新上传 Anthy 插件。

## 主要 mod 功能

当前 mod 版包含这些用户可见改动：

- 键盘左右边距可分别设置，便于单手输入。
- 可在键盘顶部始终显示数字行。
- 数字行附近可优先点击候选词，降低候选词和数字行边界误触。
- 可选“数字行输入不提交首选候选”，有拼音或候选时数字键直接输入数字。
- 英文/中文布局按 Gboard 风格补充数字行和下划符号布局。
- 长按候选符号优先当前符号，左右滑动切换其他符号。
- 剪贴板按近期记录和置顶内容分区显示，并支持手动新增置顶文本。
- AI 翻译面板，支持 DeepSeek API、自定义 API Key、目标语言选择、翻译预设和发送后保持面板打开。
- Anthy 插件加入更接近拼音输入的候选词体验。
- 主题设置增加键盘背景模糊、背景暗化、按键释放扩散和按下缩放等视觉效果。
- 按键材质支持默认、通透玻璃、磨砂玻璃和液态玻璃。
- 自定义图片背景可选择按键对比度模式，支持自适应、深色文字和浅色文字，改善复杂背景下的可读性。

## AI 翻译说明

AI 翻译功能会在用户确认发送后调用外部 API。为了避免浪费 token，它不会在输入过程中持续请求。

注意事项：

- API Key 只应填写自己信任的服务商 key。
- 翻译文本会发送给所配置的 API 服务。
- 发送前请避免输入敏感内容。
- API Key 在本地通过 Android 安全存储能力保存；不要把包含 key 的调试日志或备份发给他人。

## 已知限制

- 这是个人 mod，不保证所有上游使用场景都覆盖测试。
- 目前主要测试 arm64-v8a。
- debug/dev/release 构建不建议混用作为日常输入法。
- AI 翻译先以 DeepSeek 兼容流程验证，其他自定义 API 需要逐步测试。
- Anthy 插件仍需要按 release notes 标记的兼容版本测试，避免候选词协议或数据文件不匹配。

## 反馈和记录

用户可感知变化记录在 [CHANGELOG.md](CHANGELOG.md)。正式发布时，GitHub Release 页面会附带对应版本的 release notes；这些 notes 在发布前维护于 `release-notes/<tag>.md`。
