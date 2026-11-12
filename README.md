## Android 5.13.0

Continues the upstream 0.1.24 host port with offline TypeScript module compilation, debounced provider suggestions, paginated comments and source-acknowledged comment likes/posts, episode-aware favorites, continuous station fetching, rich track actions and native queue editing. Native setting types include masked passwords, booleans and validated selects. Provider login/settings changes invalidate other manager instances before their next call. A hash-pinned SoundGo adaptation uses Android's HLS player and optional in-app web sign-in while preserving the original provider authorization and installed source. The interface displays the installed Android version.

This is protocol coverage and Android adaptation work, not a claim that every third-party plugin, authenticated account or desktop binary is fully supported. Detailed boundaries and checks are tracked in docs/full-port-status.md.

## Android 5.12.0

Six saved native lyric display styles: character pop, immersive flow, two-line karaoke, large focus, classic scrolling, and neon gradient. Album-derived fluid/static backgrounds, font size, spacing, alignment, existing translation, accent palette and motion strength are adjustable. Reduced-motion mode keeps source-timed highlighting. Native Android tests export each preset and verify playback-position sampling, real settings controls, preference restoration and artwork ownership. Cover callbacks are matched to the current track.

## Android 5.11.0

- Follow upstream requestHeaders for authenticated images and audio, including live settings/storage interpolation and explicit stream-header precedence.
- Keep plugins from different repositories with the same declared id installed separately. Existing source ids and accounts stay valid on upgrade.
- Resolve media redirects without forwarding saved account headers across origins.
- Add installed-source diagnostics using the phone’s saved session, with direct retry, account/settings access and a listen action. Show the packaged per-source check record in the market.
- Run the existing Android regression suite plus a read-only audit of all 64 pinned market repositories. A successful install is not a playback certification. Account, subscription, private-server and desktop-helper conditions are reported separately.

# MyGo Music Android

基于 MyGo Music 插件协议的 Android 横屏适配版，当前开发版本 5.10.0。

## 本版更新

- 默认歌词效果改为逐字弹起：每字依次上浮、缩放、点亮并弹性回落，保留柔和与简洁高亮选项。
- 动效按实际播放位置采样，暂停冻结，拖动和切歌清理旧状态；保留来源时间轴与原生换行。
- 原生扫码与网页登录窗口都支持保存二维码到相册，之后从对应音乐 App 的扫一扫中选择图片，再返回等待确认。
- [开源方案比较、许可证、依赖及已知问题](docs/lyric-motion-research.md)。

- 插件网络请求收到响应头即返回，调用读取内容时才下载响应正文，音频镜像探测不再等待完整音频。
- 取消请求和关闭插件会关闭真实网络连接；读取正文仍限制 8 MB，并检查单次消费。
- QQ 搜索遇到指定临时错误 2001 时最多尝试三次，失败仍显示原错误，不重试收藏或账号写入。

- 全屏歌词设置增加同步调整，每次提前或延后 0.2 秒，范围为前后 10 秒。
- 按歌曲保存同步设置，切歌时独立恢复，重新打开歌词后保留；备份与导入包含同步设置。
- 点按歌词跳转使用调整后的时间，进度条和音频播放保持原时间轴。

- 切换音乐源后，主页内容在横屏主区域直接加载，支持自定义分类、查看更多和返回。
- 搜索类型包含歌曲、专辑、歌手、歌单和播客；按来源返回的 more/offset 加载分页，处理重复项和空页。
- 专辑、歌手、播客、电台及发布者页面可打开；收藏专辑、关注歌手、订阅播客调用来源的真实接口。
- 从来源页面播放时建立该页的独立队列，下一首、上一首和队列选曲使用同一个播放服务。
- 保留全屏逐字歌词、发光效果、离线缓存、后台播放及原有插件管理。

GitHub Actions 在 Android 13 模拟器中验证实际安装的 JavaScript 插件、页面点击及分页参数、收藏与关注状态、切换来源后的异步结果隔离，以及带来源请求头的真实音频队列。新增检查验证真实同步按钮、延后与提前方向、音频不受调整影响、跳转补偿、重新打开和切歌隔离。完整检查还保留原有歌词、账号、缓存及后台播放测试，最后验证 v5.6.0 签名覆盖升级后生成 APK。

## 范围

插件支持取决于来源账号、网络及插件是否要求桌面专用工具。远程播放来源通过其真实协议控制已有设备。并非所有桌面可执行文件都能在 Android 运行。

歌词估算逻辑移植自 https://github.com/egoist/mygo-music 的 internal/lyrics/lyrics.go，遵循 AGPL-3.0。对应源码和构建文件公开于 https://github.com/nmm26301-maker/mygo-music 。完整协议见 app/src/main/assets/third-party/AGPL-3.0.txt。
