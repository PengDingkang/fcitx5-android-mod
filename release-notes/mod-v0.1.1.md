# mod-v0.1.1 Preview

This is an unofficial preview build of Fcitx5 Android Mod.

Based on upstream fcitx5-android commit: 4dda210837eb2dda3298933c466080c9b62bc29f

Release build version: mod-v0.1.1-aN

`aN` means this mod branch is N commits ahead of upstream `master` at release time. The release workflow appends the exact value to the GitHub Release notes.

## Install

- Use release APKs for daily testing.
- Official upstream APKs cannot be upgraded in-place because signatures differ.
- If you are migrating from the official upstream app, export your data first, uninstall the official app, then install this mod build.

## Plugin Artifacts

- Anthy: unchanged. Use the latest compatible Anthy APK from an earlier mod release unless this release uploads a new plugin APK.

## Added

- Clipboard entries are split into Recent and Pinned sections, keeping recent clipboard history above pinned items.
- The clipboard panel has a `+` action for manually adding pinned text.
- Theme settings now include key material choices: Default, Translucent glass, Frosted glass, and Liquid glass.
- Theme settings now include keyboard background blur, keyboard background dimming, key release ripple, and key press scale options.
- Custom image background themes now have a Key contrast option with Adaptive, Dark text, and Light text modes.

## Changed

- The add-pinned-text flow now uses clearer title, hint, and save labels instead of the generic clipboard edit wording.
- Transparent and glass key styles apply readability protection for key and popup text on image backgrounds.
- Custom image background brightness changes update the preview key contrast behavior immediately.

## Known Issues

- This version is a preview release.
- Primary test target is arm64-v8a.
- Visual effects are still experimental and may look different across Android versions and device GPUs.
