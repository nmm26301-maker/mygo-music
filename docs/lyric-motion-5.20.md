# 5.20 歌词动效扩展与开源方案比较

核查日期：2026-11-13。先核对已有六种样式、当前 GitHub 源码、维护时间、许可证、依赖和公开问题，再移植。应用继续使用 Android Canvas/StaticLayout，没有重新初始化或替换音频播放器。

| 项目 | 维护与许可 | 实现、依赖与问题 | 本轮选择 |
| --- | --- | --- | --- |
| [AMLL](https://github.com/amll-dev/applemusic-like-lyrics) | 2026-11-11 更新；AGPL-3.0-only；固定 832bad5f16ec2a7f96d06707ef927c9a4d3713d3 | DOM/TypeScript、Web Animations、bezier-easing/gl-matrix，Pixi 相关 peer dependencies；README 列出浏览器和高帧率性能条件。#639、#638、#601 涉及中英混排断行，#633 涉及意外高亮。 | 移植 base/spring.ts 正常播放的弹簧参数策略，保留原文件和许可证说明。参考短词持续时间强调及逐字符错峰的设计，原生实现取样与画面，不移入 DOM 排版和整套浏览器运行库。 |
| [Accompanist Lyrics UI](https://github.com/6xingyv/accompanist-lyrics-ui) | 2026-11-01 更新；Apache-2.0 | Kotlin/Compose Multiplatform、lyrics-core。当前 README 展示多声部和伴奏行；它采用 Compose UI，不能直接作为现有 Java/StaticLayout 组件插入。 | 对照布局和按绘制时钟读取状态的设计，保留现有 StaticLayout 与换行、译文。没有复制源文件或增加 Compose 依赖。 |
| [Spicy Lyrics Mobile](https://github.com/spicylyrics/mobile) | 2026-11-05 更新；AGPL-3.0 | Kotlin/Jetpack Compose，逐字起伏、辉光与动态背景；README 明确早期开发、界面及来源可能变化，并提供低性能模式。 | 对照长音逐字起伏与减少动态效果的选择，不引入它的歌词检索、翻译和网络服务。 |
| [AMLV](https://github.com/dokar3/amlv) | 最近推送 2023-12-20；Apache-2.0 | Android/Jetpack Compose，Apple Music 式歌词与背景；较久未更新。 | 仅作为较早原生方案对照，不作为本轮主要依赖。 |

## 实际加入

1. 弹簧聚焦：换行时使用 AMLL 的时长映射参数，当前行趋近焦点，后续可见行按小幅延迟错峰滑动；用缩放和透明度形成前后层次，不为每行反复做高成本模糊。
2. 长音波浪：保留弹簧跟随，对来源中持续至少 1 秒且长度较短的词增加逐字符起伏、放大和辉光。效果由来源时长驱动，不声称识别真实音高、重音或鼓点；不对推算词或无时间轴文本制造长音信息。双向文字保留原生整词高亮，避免破坏字形连接。
3. 歌词页顶部入口改为动效，新选项排在快捷菜单顶部；原六种样式保留。样式与操作菜单分组，新增编号不会覆盖重新获取、导入或同步调整。原有样式选择不被强制重置。
4. 暂停、回退与拖动按实际歌曲时间取样；减少动态效果、动效强度为 0 和系统关闭动画时停止起伏与弹簧平移，仍保留实际演唱进度。

## 许可证和来源

Java 参数策略来自 packages/core/src/lyric-player/base/spring.ts；未修改的文件随源码保存在 third-party/amll/pos-y-spring.ts，SHA-256 ade1d10115fe496046b231aa2a454f800b703360b2bb21fc9a6f1cf22d1301ff。APK 附带 third-party/AMLL-NOTICE.txt，完整 AGPL 文本沿用 third-party/AGPL-3.0.txt。

AMLL utils/spring.ts 的求解器带有另一个 MIT 来源提示，本轮没有复制该文件或求解器，避免把来源提示当作完整许可。Android 连续时间求解与可见行几何为本项目实现；长音包络采用原生计算。已有 KaraokeText 的 MIT 包络与声明保留。

## 验证方法

执行固定指纹的原版 TypeScript 参数函数，生成 7 组独立期望值供 Android 与 Java 实现比较。新增 Canvas 像素与实际快捷菜单检查，验证错峰行位置、长音效果区别、暂停及回退逐像素一致、减少动态效果、纯文本、保存选项，以及新增样式不会截获旧操作。保留所有既有歌词、真实音频、登录与插件检查，并导出原生渲染帧供预览。

云端回归、覆盖安装与交付文件校验完成后补充最终记录。
