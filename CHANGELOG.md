# Changelog

本文件记录 mod 版的用户可见变化。内部重构、CI 调整和上游同步只在影响安装、升级、输入行为或构建产物时记录。

当前公开预览版从 `mod-v0.1.0 Preview` 开始发布。

正式发布前，为对应 tag 准备 `release-notes/<tag>.md`。手动发版 workflow 会优先使用该文件作为 GitHub Release notes；如果文件不存在，会使用默认说明。

## Unreleased

### Added

- 剪贴板面板按“近期记录”和“置顶”分区显示，近期记录始终位于置顶项上方。
- 剪贴板面板增加 `+` 入口，可手动新增置顶文本。
- 主题设置增加按键材质选项：默认、通透玻璃、磨砂玻璃和液态玻璃。
- 主题设置增加键盘背景模糊、背景暗化、按键释放扩散和按下缩放等视觉效果选项。
- 自定义图片背景增加“按键对比度”选项，可在自适应、深色文字和浅色文字之间选择。

### Changed

- 新增置顶文本页面使用独立标题、输入提示和保存按钮，避免和编辑剪贴板历史项混淆。
- 透明/玻璃材质下的按键和弹出按键会进行可读性保护，降低图片背景或半透明按键导致文字看不清的概率。
- 自定义图片背景的亮度调整会同步刷新预览中的按键对比度效果。

## mod-v0.1.0 Preview

### Added

- 增加 mod 版用户说明和 changelog。
- 增加手动发版 workflow：输入 tag 后构建 release APK、创建 tag、发布 GitHub Release，并优先读取 `release-notes/<tag>.md` 作为 release notes。
- 单手输入相关设置：键盘左右边距可分别调整。
- 始终显示数字行模式。
- 数字行附近候选词触摸优先，降低候选词和数字行边界误触。
- 可选“数字行输入不提交首选候选”。
- 中文/英文键盘布局按 Gboard 风格补充数字行和下划符号。
- AI 翻译面板，支持 DeepSeek API、自定义 API Key、目标语言选择和翻译预设。
- 可选发送后不自动关闭翻译面板。
- Anthy 插件候选词输入体验改进。
- 本地 release signing 和 CI release signing 分离。
- debug 数据导入兼容性修复。

### Changed

- Build CI 只在输入法本体、Anthy 插件或构建相关路径变化时触发。
- 手动发版 workflow 使用 `<tag>-aN` 作为 release 构建版本名，其中 `N` 是当前 mod 分支相对上游 `master` 的 ahead commit 数；Android `versionCode` 仍从 tag 推导。
- 手动发版 workflow 校验 release notes 中记录的上游 `master` commit，必须和计算 `aN` 时使用的 `origin/master` 一致。
- 手动发版 workflow 增加 Anthy 插件产物选项：`auto` 仅在首次发版或 `plugin/anthy` 有变化时上传，也可手动选择包含或跳过。

### Known Issues

- 该版本标记为 pre-release。
- 主要测试目标为 arm64-v8a。
- 从上游官方版迁移到 mod 版不能直接覆盖安装，需要先导出数据并重新安装。
- AI 翻译请求会把待翻译文本发送给配置的 API 服务。
