# 逐字弹起动效选型

核查日期：2026-11-12。先检索并读取仓库、许可证、依赖、动效源码和公开问题，再修改安卓渲染器。

| 项目 | 维护与许可证 | 实现思路与依赖 | 已知问题与适配判断 |
| --- | --- | --- | --- |
| [AMLL](https://github.com/amll-dev/applemusic-like-lyrics) | 2026-11-11 更新；AGPL-3.0-only；核查提交 832bad5f16ec2a7f96d06707ef927c9a4d3713d3 | 字符错峰、上浮、缩放和辉光；DOM、Web Animations、bezier-easing，另有 React/Vue 绑定 | [601](https://github.com/amll-dev/applemusic-like-lyrics/issues/601) 讨论中英混排断行；[633](https://github.com/amll-dev/applemusic-like-lyrics/pull/633) 讨论行切换意外高亮。本项目保留 Android StaticLayout 的换行与播放时钟，借鉴逐字符调度设计，不整包引入 Web UI。 |
| [am-lyrics](https://github.com/binimum/am-lyrics) | 2026-11-07 更新；MPL-2.0；版本 1.9.0 | Lit Web Component，Babel runtime，React 绑定可选；播放位置更新、暂停及字符上浮 | README 明确 SSR 支持有限；历史已关闭问题 [15](https://github.com/binimum/am-lyrics/issues/15) 涉及循环播放滚动复位，[16](https://github.com/binimum/am-lyrics/issues/16) 涉及 React 适配。适合作为暂停/跳转行为对照，不额外引入 Lit。 |
| [KaraokeText](https://github.com/WillSuo-Github/KaraokeText) | 2026-08-17 最近更新，更新较少；MIT；提交 08697a112d314aa360f9b2ebed3622181ab58ad8 | SwiftUI TextRenderer；逐字 smoothstep 上浮、保持、指数衰减余弦回弹；按真实播放时间采样 | 要求 iOS 18 等 Apple 平台，不能作为 Android 依赖直接使用；本次只移植许可证明确的动效包络，调整幅度与节拍，并保留完整 MIT 声明。没有开放 issue 不代表没有问题。 |

## 本次实现

- `LyricsCharacterMotion.java` 移植 KaraokeText 的上浮与衰减回弹设计，完整许可证随 APK 放在 `third-party/KaraokeText-MIT.txt`。
- `LyricsStageView.java` 使用现有 StaticLayout 位置与原生 drawTextRun 绘制，保留字体上下文，不引入 SwiftUI、Lit 或浏览器动效运行库。
- Android 7 及以上使用 ICU 字素边界，避免拆开代理对、组合重音和 ZWJ 表情；Android 6 使用系统 java.text.BreakIterator。
- 每字单独移动、放大、亮起及回落，不将整句作为一个缩放对象；同一来源词内有多个字时按原词时间范围和字宽错峰，不伪造来源逐字时间。
- 动效完全由播放器位置计算；暂停冻结，后退直接重算，切歌清理排版和字符缓存。没有时间轴的纯文本不制造演唱时间。
- 右到左及混合双向文本暂保留原高亮排版，避免逐字符变换破坏字形连接。
- 安卓测试验证实际像素上移、放大、后续字符未提前移动、暂停/回退一致性、字素完整性；导出实际原生渲染帧用于检查效果。

## 二维码

继续使用已有 ZXing 生成二维码与 Android MediaStore 保存到相册，保持登录窗口等待确认。补充实际保存按钮测试，并从相册读回 PNG 解码核对原始二维码内容。正式账号扫码确认由用户在对应 App 内完成。
