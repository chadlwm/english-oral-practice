# 英语口语练习（English Oral Practice）

基于本地视频的 Android 英语口语练习应用。

## P0 功能

- **导入视频（US1）**：通过 SAF 选择本地视频；持久化 URI 权限；白名单 mp4/mkv/webm；损坏/不支持有明确提示
- **视频列表（US2）**：展示标题与时长；坏条目标记且不拖垮列表；支持重新授权与删除
- **字幕（US3）**：支持内嵌轨与外挂 `.srt`；外挂优先；坏 SRT 回退内嵌/无则关轨；开关只控制当前生效源
- **播控（US4）**：前进/后退、整段循环、A-B 循环；A-B 与 seek 同一状态机；ExoPlayer 单实例进出页释放

P1 跟读评分、P2 学习报告尚未实现。

## 环境要求

- Android Studio Hedgehog 或更新版本
- JDK 17
- compileSdk 34 / minSdk 26

## 本地构建

```bash
./gradlew assembleDebug
```

Debug APK 路径：`app/build/outputs/apk/debug/`。也可用 Android Studio 直接 Run。

### 安装 Debug 包

1. 开启手机「未知来源」或通过 `adb install app/build/outputs/apk/debug/*.apk`
2. 当前产物为 **debug/未签名**，仅供开发与内测，**不可上架**

## CI / CD

| 触发 | 行为 |
|------|------|
| push / PR → `main` | 运行 `assembleDebug`，失败必红；成功上传 APK Artifact（不发 Release） |
| 推送 `v*` tag（如 `v0.1.0`） | 编译 debug APK 并创建 GitHub Release；`versionName` 取自 tag，`versionCode` 为 CI run number；附件为 debug/unsigned，说明不可上架 |

工作流文件：`.github/workflows/ci.yml`、`.github/workflows/release.yml`。

### 验证首发 Release

```bash
git tag v0.1.0
git push origin v0.1.0
```

然后到仓库 Releases 页查看附件与说明。

## 技术栈

Kotlin、Jetpack Compose、Media3/ExoPlayer、Room、SAF。

## 许可证

MIT
