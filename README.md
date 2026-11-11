# MyGo Music Android

基于 MyGo Music 插件协议的 Android 横屏适配版，当前开发版本 5.6.0。

## 本版更新

- 全屏大字歌词、连续逐字渐亮、发光与柔和/简洁高亮、译文、滑动浏览及点击跳转。
- 优先采用插件提供的绝对毫秒逐字时间；普通 LRC 按原版的字词长度及 85% 行时长估算，并在页面标注。支持增强 LRC 导入及刷新缓存歌词。
- 歌词直接使用播放服务的真实时钟，暂停、拖动、切歌同步。
- 前台播放服务持有唯一原生播放器、系统媒体会话及通知控制；退出页面、后台切歌不重建正在播放的音频。

GitHub Actions 在 Android 13 模拟器中验证原生插件调用、实际音频、歌词绘制像素、后台控制及 v5.5.0 签名覆盖升级，再生成签名 APK。

## 范围

插件支持取决于来源账号、网络及插件是否要求桌面专用工具。远程播放来源通过其真实协议控制已有设备。并非所有桌面可执行文件都能在 Android 运行。

歌词估算逻辑移植自 https://github.com/egoist/mygo-music 的 internal/lyrics/lyrics.go，遵循 AGPL-3.0。对应源码和构建文件公开于 https://github.com/nmm26301-maker/mygo-music 。完整协议见 app/src/main/assets/third-party/AGPL-3.0.txt。
