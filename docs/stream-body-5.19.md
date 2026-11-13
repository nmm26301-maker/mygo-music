# 5.19 插件流式响应兼容

5.18 的扫码、登录入口和横屏图标已完成验证，本轮继续补齐插件运行环境的真实差异。核对原版 egoist/mygo-music 的当前 main 仍为 0.1.24，因此保留最低协议检查，不把版本号改成 0.1.31 来强装尚未核验的插件。

## 已发现的问题

DaviRain-Su/mygo-music-qishui 原版目录读取会调用 response.body.getReader()，循环解码字节，并在异常时取消 reader。原安卓 fetch 只提供 text、json 和 arrayBuffer，没有 body 流对象，代码会在读取响应时失败。这是宿主兼容缺口，不等于账号没有会员。

## 参考、许可和依赖

- egoist/mygo-music，AGPL-3.0，当前 552e25c04023aa00c551d77db69aaab16f60bf2d：上游实际使用 MoeForge/moejs 的 Web API，本轮保留已有 Android 项目与协议约束。
- MoeForge/moejs，Apache-2.0，2026-11-09 维护：参考其惰性响应体、单消费者/锁定、逐块读取和取消网络连接的设计。其 Go 引擎与 WebView 架构不同，没有直接引入 Go 运行时或复制其实现。
- node-fetch/node-fetch，MIT：对照了标准 Response/流的 API；它依赖 Node 网络与流模块，不适合直接塞入 Android WebView，本轮未增加依赖。
- WHATWG Streams 与 Fetch 标准：使用 WebView 自带的 ReadableStream、Response 和 TextDecoder 来处理锁定、bodyUsed、克隆、二进制及跨块 UTF-8，原生层只提供有界的按需字节拉取。参考 https://streams.spec.whatwg.org/ 与 https://fetch.spec.whatwg.org/ 。
- DaviRain-Su/mygo-music-qishui，AGPL-3.0，2026-11-10 更新，固定提交 68ff332fe60eaa869f4a9afc55cc2144e151e312：直接用原版读取函数验证宿主，不改写该来源，不复制其登录或客户端辅助程序。原 index.js SHA-256 为 d004a53dc1e6a63546919cbb92a662121dd6691b5cfe296d54bd6f7ab8cbe1c5。

## 实现

响应仍在收到 HTTP 头后返回，默认不预读正文。每次 reader 请求最多拉取 32 KiB，累计仍限制 8 MiB；取消或关闭插件会中止真实套接字，读取线程负责关闭正在使用的输入流，空闲流在独立后台清理池释放。Android 的 HTTP 关闭操作可能读取剩余字节，不能在界面线程执行，也不能等待被读取任务占满的工作池。未读取的响应仍有过期清理，不会无限占用连接。请求头、Cookie、网络域名权限与跨源重定向规则继续保留。

现代 WebView 的 Response 提供 text/json/arrayBuffer/blob、标准 body 所有权和克隆，克隆保留宿主 URL、重定向及 getSetCookie 元数据。对会把响应读取异常改为通用错误的旧 WebView，正文消费方法保留请求的取消和网络失败原因；已经消费或锁定的正文仍报标准 TypeError。HEAD 和无正文状态不创建正文流。没有流原语的旧系统 WebView 保留此前的有界整包路径，不承诺其提供 getReader。此轮不宣称完整实现所有 Request/流上传/BYOB 等 Fetch 能力。

网页二维码捕获增加绘制回调的超时兜底：窗口旋转时即使回调未返回，也会在动画帧后通过 PixelCopy 读取当前窗口。页面、登录会话和运行环境改变时，旧操作仍会取消；必须从实际可见像素解码成功，才允许放大或保存。放大本身不写入相册，也不结束登录等待。

## 验证与范围

本地 22 项检查已通过，包括原版汽水读取器、跨块中文、克隆、不可重复消费、锁定、提前取消、等待正文时中止、空正文和旧 WebView 回退。新增安卓测试直接连接受控 HTTP 服务器，必须在 EOF 前收到字节前缀，并确认 cancel、abort 和 dispose 关闭真实连接；同时读取本地文件并调用固定指纹的原版汽水解析函数。登录过期和验证码错误必须原样保留，不能虚报认证成功。

歌词服务测试先确认真实播放位置开始前进，再验证暂停、恢复和跳转，避免把 MediaPlayer 已发出播放命令误当作音频输出已经就绪。实际音频、歌词像素与暂停冻结断言继续保留。

这项修改修复通用网络 API，不代表已完成汽水真实用户登录、会员播放，或任意桌面辅助程序移植。原版声明的辅助程序仍须具备 Android 实现，平台账号与地区条件仍由服务端决定。

## 最终验证与恢复点

构建与完整回归对应提交 1eec6719bfe3f66bd7318efcd9f90f79c6d56571，成功 Actions：https://github.com/nmm26301-maker/mygo-music/actions/runs/43410675370 。22 项本地检查与完整 Android 13 x86_64 原生/WebView 回归通过，四个 Android ABI 均已编译并打包。5.6.0、5.18.0 使用正式签名分别覆盖升级到 5.19.0 / versionCode 69，无需卸载。

匿名实网审计共尝试 64 个固定版本，63 个安装加载成功、40 个搜索调用成功（包括合法空结果）、8 个音频 HTTP 响应通过、6 个原生解码器准备成功。各阶段分开记录，不把可安装视为可播放，也不把这些数字当作全部插件或会员账号认证。要求协议 0.1.31 与桌面工具的 YouTube Music 插件继续被明确拦截，没有提高协议编号来跳过检查。

交付文件再次校验了稳定签名、包名与版本、四种 ELF 架构、实际 plugin-host.js 与本轮原生方法、MIT/LGPL/AGPL 说明、测试类未进入生产 APK、64 来源结果、备用 ZIP 内 APK 完全一致，以及不含签名密钥的对应源码与固定 FFmpeg 完整源码。应用代码在后续进度文档提交中不再改变。

| 文件 | SHA-256 |
| --- | --- |
| mygo-music-v5.19.0.apk | 342ff00890383f34c5e42f9cdb1a49397cc52bde73e3d4c27ffb52476a242c38 |
| mygo-music-v5.19.0-install.zip | 5a95c63af841a3d5cb33dd5ef53f8a4acecd668d7cbca487bfff631c814092ac |
| mygo-music-v5.19.0-sources.zip | 3b15201ff6ef57b2647a3e080812d6db5919eddbb4897d47b08db4e4a21b3f9c |

