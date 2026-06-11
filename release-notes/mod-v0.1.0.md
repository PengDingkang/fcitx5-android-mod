# mod-v0.1.0 Preview

This is an unofficial preview build of Fcitx5 Android Mod.

Based on upstream fcitx5-android commit: TBD

## Install

- Use release APKs for daily testing.
- Official upstream APKs cannot be upgraded in-place because signatures differ.
- If you are migrating from the official upstream app, export your data first, uninstall the official app, then install this mod build.
- Install the Anthy plugin APK from the same release if you need Japanese input.

## Added

- Separate left and right keyboard padding controls for one-handed typing.
- Always-show number row mode.
- Candidate-priority touch handling near the number row to reduce mistaps.
- Optional behavior to insert number row digits directly without submitting the first candidate.
- Gboard-style number row and secondary symbol layout for Chinese and English keyboards.
- AI translation panel with DeepSeek API support, custom API key, target language selection, presets, and optional keep-open behavior after sending.
- Anthy plugin candidate input improvements.

## Changed

- Local release signing and CI release signing are separated.
- Build CI runs only for app, Anthy, or build-related changes.

## Fixed

- Debug data import compatibility for same-package development builds.

## Known Issues

- This version is a preview release.
- Primary test target is arm64-v8a.
- AI translation sends the source text to the configured API provider.
- Anthy plugin should be installed from the same release as the main app.
