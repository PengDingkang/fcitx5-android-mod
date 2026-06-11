# Changelog

本文件记录 mod 版的用户可见变化。内部重构、CI 调整和上游同步只在影响安装、升级、输入行为或构建产物时记录。

当前还没有正式 release。第一版计划以 `mod-v0.1.0 Preview` 发布。

正式发布前，为对应 tag 准备 `release-notes/<tag>.md`。手动发版 workflow 会优先使用该文件作为 GitHub Release notes；如果文件不存在，会使用默认说明。

## Unreleased

### Added

- 增加 mod 版用户说明和 changelog。
- 增加手动发版 workflow：输入 tag 后构建 release APK、创建 tag、发布 GitHub Release，并优先读取 `release-notes/<tag>.md` 作为 release notes。

### Changed

- Build CI 只在输入法本体、Anthy 插件或构建相关路径变化时触发。

## mod-v0.1.0 Preview - Planned

### Added

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

### Known Issues

- 该版本计划标记为 pre-release。
- 主要测试目标为 arm64-v8a。
- 从上游官方版迁移到 mod 版不能直接覆盖安装，需要先导出数据并重新安装。
- AI 翻译请求会把待翻译文本发送给配置的 API 服务。
