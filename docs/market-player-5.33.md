# v5.33.0 完整原生播放页

本轮对照 Spotify、Apple Music 的官方播放与队列操作说明，检查 Metro、Auxio、Gramophone 的公开项目及许可。完整应用不能作为插件直接嵌入现有 Java 播放服务；本次保留现有服务和插件协议，独立实现更集中的原生播放页。真正纳入安装包的开源部件是 Lucide 的 15 个矢量图标，包含固定版本原始 SVG、Android 衍生资源、可重建脚本和完整 ISC 及继承 Feather 图标的 MIT 许可。

| 操作 | 上一版情况 | 本轮实现 |
| --- | --- | --- |
| 点击底部封面或歌名 | 打开长设置弹窗 | 完整播放页，封面与歌曲信息、控制区域并列 |
| 常用控制 | 分散在设置和歌词页 | 播放、暂停、上下曲、随机、循环、收藏集中展示 |
| 队列、歌词、音质 | 分散入口 | 播放页直接打开原有真实功能，关闭歌词返回播放页 |
| 缓冲进度 | 播放服务未提供 | 使用 Android 原生回调，主界面、歌词页和完整播放页显示，媒体会话同步已缓冲位置 |
| 封面操作 | 点击打开旧弹窗 | 点按打开歌词；左右滑动切歌；取消及过期会话手势不提交 |
| 进度拖动 | 歌词页已有防重复提交 | 新播放页预览期间不跳转，释放提交一次，取消和新会话后拒绝旧手势 |
| 页面反馈 | 基本文字按钮 | 本地矢量控件、按压反馈、入场与封面渐显，遵守减少动态效果与系统动画设置 |
| 小窗口 | 旧弹窗易占满 | 紧凑窗口隐藏大封面，控制区域可滚动，主要触控目标至少 48dp |

打开、关闭和切换页面均操作同一播放服务，不新建第二个音频播放器。直播或未知时长不提供虚构时长，禁用不可用的进度跳转。收藏未提供接口的来源明确禁用该入口。音量、音效、睡眠定时、评论与可用的远程设备入口保留在更多菜单。

原生回归应验证真实 AAC 播放时钟、HTTP 缓冲回调、暂停与跳转、取消及旧会话手势、封面滑动切歌、队列选曲、歌词返回、原生图标和紧凑触控目标。最终测试结论另见验证记录。预览使用受控音频和测试封面，不能作为第三方歌曲授权的证明。

本轮仍未实现跨曲无缝播放、交叉淡化或拖动排序的新队列界面；原有队列长按移动、移除继续可用。第三方来源、真实会员和实体手机长时听感依然需要分别验证，不将这一版称为与商业播放器全部能力完全相同。

## 参考与开源来源

- Spotify Now Playing：https://support.spotify.com/us/article/now-playing/
- Spotify Play Queue：https://support.spotify.com/pg/article/play-queue/
- Apple Music 队列：https://support.apple.com/en-ie/109336
- Metro：https://github.com/MuntashirAkon/Metro
- Auxio：https://github.com/OxygenCobalt/Auxio
- Gramophone：https://github.com/FoedusProgramme/Gramophone
- Lucide：https://github.com/lucide-icons/lucide/tree/55782beff3a0133df79c417eb68f96e8f19e2404
- Lucide 许可：https://lucide.dev/license
- Android 缓冲回调：https://developer.android.com/reference/android/media/MediaPlayer.OnBufferingUpdateListener

Lucide 固定版本为 55782beff3a0133df79c417eb68f96e8f19e2404。第三方目录保留原始图形和逐文件校验和；tests/generate-player-icons.py 可重新生成 Android 资源。未引入网页图标运行时或联网图标请求，也未声称移植上述完整播放器的内核。

## 视觉检查发现并补充修复

第一轮原生流程虽通过，但紧凑截图被插件未启用弹窗遮挡。核查确认：非插件歌曲带元数据时，界面错误写入空来源的插件引用。现仅为真实插件会话写入引用，并清理旧空来源记录；合法的旧直链插件来源仍保留，以支持原有歌词与评论桥接。新增回归要求真正加载两行定时歌词并核对原来源调用，而不只确认歌词窗口打开。最终交付必须使用修复后的完整回归与截图。
