# 时间水印相机

独立的 Android 拍摄应用，位于本仓库的 `timestamp-camera/`。用 Android Studio 打开这个目录即可，不会参与 Kaku 的 Rust 构建。

## 功能

- **逐帧时间水印**：拍摄时每一帧都会画上当时的时间，格式为 `2026年11月06日` 和 `22时41分05秒`。水印进入预览，也进入保存下来的 MP4，不是只浮在界面上。
- **录音**：默认打开麦克风，和画面一起写入视频。可以在开拍前关掉。
- **快捷字幕**：预先写好几条固定文案。拍摄页或回看页点一下，字幕就记到当时的时间线上。
  - 拍摄中点选的字幕会同时画进画面，并一直保持到再点一次或换成另一条。
  - 回看时点选的字幕从播放头起记 3 秒，叠在播放器上，并可以导出 SRT。已经写进画面的字不会因为从时间线删除而消失。

录像保存在系统相册的 `Movies/TimestampCam`。字幕时间线存在应用私有目录，导出的 SRT 会进下载目录，也可以从分享面板发出去。

## 构建

需要 JDK 21 和 Android SDK（compileSdk 35）。

```bash
cd timestamp-camera
# 把 sdk.dir 指到本机 Android SDK
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew :core:test :app:assembleDebug
```

调试包在 `app/build/outputs/apk/debug/app-debug.apk`。最低系统版本是 Android 8.0（API 26）。
