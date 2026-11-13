## Android 5.14.0

Same-phone gallery QR login: save the clean QR image before opening the correct scanner app. QQ authorization opens mobile QQ, WeChat opens WeChat, and NetEase/Kugou/Bilibili codes use their matching apps. Missing-app guidance and stale handoff guards retain pending authorization. Native and web login windows expose save-and-open actions. Size/orientation changes retain the login runtime while handing off to a portrait app; the local library reflows on return. No private scanner Activity names or automatic account confirmations are used.

Verified Android 13 tests and stable-signed upgrades from 5.6.0 and 5.13.0: [same-phone flow and test scope](docs/phone-qr-login.md). Platform restrictions on recognizing particular login codes from an album still apply. The remaining migration boundaries in docs/full-port-status.md are unchanged.

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

基于 MyGo Music 插件协议的 Android 横屏适配版，当前版本 5.20.0。

## 本版更新

- 5.20：新增弹簧聚焦、长音波浪，共八种显示样式；在歌词页顶部动效菜单中选择，新效果排列在前，保留原有选择。
- 5.20：移植固定版本 AMLL 的弹簧参数策略，按歌曲时间实现错峰跟随和持续字词起伏；暂停冻结、回退重算，并遵循减少动态效果和系统动画设置。
- [开源方案、许可、验证范围和最终记录](docs/lyric-motion-5.20.md)。已通过完整安卓回归及 5.6/5.19 原签名覆盖升级。

- 5.19：补齐插件 response.body.getReader() 按需读取，兼容跨块中文解码、取消、克隆和单次消费。固定指纹的原版汽水响应解析函数用于验证，账号与验证码错误继续保留。
- 5.19：关闭未读完的响应改为后台清理，避免退出插件时触发 Android 界面线程网络异常；网页二维码捕获增加绘制回调的超时兜底。
- [流式响应实现、开源方案比较与验证范围](docs/stream-body-5.19.md)。这项兼容修复不代表汽水桌面辅助程序、真实账号或会员播放已全部移植。
- 5.19 已通过 22 项本地检查、完整 Android 回归、64 来源分阶段检查、原签名 5.6/5.18 覆盖升级及交付文件校验。

- 5.18：原生二维码前置显示，支持点击全屏放大；网页二维码可放大和刷新，保存/打开操作保持可见。
- 5.18：B站已核验版本优先使用原插件扫码生成与轮询；喜马拉雅补齐官方 App 路由和包可见性，官网登录页使用更大的竖屏窗口。
- 5.18：横屏来源栏固定账号入口；只有账号配置的插件进入设置，导航和播放控制图标改为本地矢量。
- [本轮实现、原版源码与许可证、验证范围](docs/login-qr-5.18.md)。
- 5.18 已通过完整 Android 回归、原生与网页二维码像素解码、原签名 5.6/5.17 覆盖升级与交付文件校验。真实用户账号与会员不以受控测试代替验证。

- 5.17：QQ 摄像头授权不再引导相册选图。已核验插件默认进入 QQ 音乐官网，按官网实际提供的方式登录，取得音乐凭据后经原账号接口确认再保存和取流。
- 若官网仍要求摄像头扫码，需要另一块屏幕展示二维码，本机相册不能代替这项验证。
- [官网入口、平台限制、测试范围和构建记录](docs/qq-official-login.md)。
- 5.17 已通过完整 Android 回归、64 来源分阶段检查及 5.6/5.16 正式签名覆盖升级。真实账号与会员不以模拟测试代替验证。

- 5.16：扫码窗口新增刷新，保存及打开扫码 App 前重新取码；取消旧等待并拦截已关闭会话的迟到账号写入。
- 5.16：酷狗及概念版保留歌曲、专辑和登录设备信息，补齐当前账号校验与歌曲授权取流；缺少来源登录、凭据失效、设备验证和空地址分别提示，不再把空地址统一当作会员限制。
- [本轮修复、原版源码指纹、账号验证范围和成功构建](docs/login-playback-repair.md)。

- 支持相册识别的平台可保存二维码并打开对应扫码 App，在同一手机选图后返回播放器等待确认。明确要求摄像头的授权码不能走相册流程。
- 保留六种歌词效果和逐字时间轴，默认逐字弹起，支持暂停、拖动、双语、歌词同步微调。
- 增加来源 gain/peak 音量均衡，播放设置中可关闭；离线文件保留增益信息。
- 同一音乐源的设置跨窗口依次保存，失败回滚，旧窗口不覆盖较新的值；远程电台只在真实播放结束后续播。
- 增加四种 Android 架构的 ALAC 解码与完整 HLS 离线缓存。分段音频通过本机代理保留来源请求头，转换为完整 FLAC 后才加入缓存。直播和受 DRM 保护的清单不写入离线歌曲。
- 解码器从校验固定版本的 FFmpeg 9.0.2 官方源码构建，仅启用 LGPL 功能，随安装包提供完整对应源码和构建配置。它是应用内部音频组件，不代表任意桌面 FFmpeg 命令都可运行。
- [完整移植记录和实际边界](docs/full-port-status.md)。

- 默认歌词效果改为逐字弹起：每字依次上浮、缩放、点亮并弹性回落，保留柔和与简洁高亮选项。
- 动效按实际播放位置采样，暂停冻结，拖动和切歌清理旧状态；保留来源时间轴与原生换行。
- 允许相册识别的扫码窗口支持保存二维码并打开对应 App；QQ 官网登录不提供相册授权按钮，摄像头扫码方式会说明另一屏幕要求。
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
