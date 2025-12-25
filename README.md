# Emby TV 客户端 / Emby TV Client

简体中文 | English

---

## 简介 / Introduction



## 下载 / Download

 最新版本 / Newest release: v1.0.1



[Android apk release](https://github.com/shareven/emby_tv/releases/)


![GitHub All Releases](https://img.shields.io/github/downloads/shareven/emby_tv/total)

## 特性 / Features

- 支持 TV/遥控器焦点与按键交互
- 播放器（支持直接播放与转码信息展示）
- 选集与剧集导航（Series/Episodes）
- 简中/英文本地化（跟随系统语言）

- Focus and key handling for TV remotes
- Player with direct stream/transcode info
- Series / Episodes navigation
- Localization: Simplified Chinese and English (follows system locale)

## 快速开始 / Quick Start

先安装依赖并运行：

First install dependencies and run:

```bash
flutter pub get
flutter run
```


## 本地化 / Localization

本项目在 `lib/l10n/app_localizations.dart` 中维护中/英文本，界面会根据系统语言自动选择。若要新增翻译，请在该文件中添加键。

Localization entries live in `lib/l10n/app_localizations.dart`. The app chooses the language following the system locale. To add translations, extend the maps in that file.

## 贡献与交流 / Contributing

欢迎通过 Issues 或 PR 交流问题与改进想法。此项目以学习交流为主，代码风格可能更偏向演示与实践用途。

Please open Issues or PRs for bugs or improvements. This project is primarily for learning and technical exchange, and code may be organized for demonstration purposes.

## 许可 / License

本项目使用禁止商业用途的许可：Creative Commons Attribution-NonCommercial 4.0 International (CC BY-NC 4.0)。

简要说明：允许复制、分发和改编，但禁止用于商业用途，使用时需注明作者并链接到许可协议。

This project uses a non-commercial license: Creative Commons Attribution-NonCommercial 4.0 International (CC BY-NC 4.0).

summary: You are free to copy, distribute, and adapt the work, as long as you don't use it for commercial purposes. When you do, you must attribute the work and include a link to the license.

许可证原文与条款请见 / License Text and Terms :

https://creativecommons.org/licenses/by-nc/4.0/legalcode

SPDX-License-Identifier: CC-BY-NC-4.0
