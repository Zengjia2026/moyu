# 摸鱼 VPN Android

一个极简 Android VPN 客户端：导入 Clash/Mihomo YAML 配置后直接连接，并实时显示 VPN 上下行速度与本次累计流量。

## 已实现

- Android `VpnService` 系统 VPN
- 从系统文件选择器导入 `.yaml` / `.yml`
- 配置保存在 App 私有目录，不上传到服务器
- 嵌入 Mihomo 核心，支持配置中的 VLESS / REALITY / Vision 等能力
- 一键连接、断开
- 每秒读取 Mihomo 实际代理流量：上传 / 下载 bytes/s
- 本次累计上传 / 下载流量
- 前台服务通知

## 构建环境

- JDK 17
- Android Studio / Android SDK 36
- Gradle 8.13（建议）
- AGP 8.12.2
- Kotlin 2.2.10
- minSdk 26

首次构建会从 `oviron/libmihomo-android` 的 GitHub Release 下载固定版本 `v0.3.7` AAR。

在 Android Studio 中打开项目后直接 Sync / Run 即可；也可以使用本机 Gradle 8.13 执行：

```bash
gradle :app:assembleDebug
```

APK：`app/build/outputs/apk/debug/app-debug.apk`

仓库已配置 GitHub Actions 自动构建：提交到 `main` 后会生成 `moyu-vpn-debug-apk` Artifact。

## 使用

1. 安装 APK。
2. 点「导入 YAML 配置」。
3. 选择 Clash/Mihomo 格式的 `.yaml` / `.yml`。
4. 点「连接 VPN」。
5. 首次使用允许 Android VPN 权限。
6. 首页会实时显示上传、下载网速以及累计流量。

## 安全说明

不要把真实节点配置提交到公开 GitHub 仓库。导入的配置仅复制到 App 私有目录 `files/profiles/active.yaml`。

## License

项目集成的 Mihomo Android 库与 Mihomo 核心采用 GPL-3.0。若分发包含该核心的 APK，请按对应 GPL-3.0 要求处理源代码与许可。
