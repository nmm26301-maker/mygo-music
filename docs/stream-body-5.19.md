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

现代 WebView 的 Response 提供 text/json/arrayBuffer/blob、标准 body 所有权和克隆，克隆保留宿主 URL、重定向及 getSetCookie 元数据。HEAD 和无正文状态不创建正文流。没有流原语的旧系统 WebView 保留此前的有界整包路径，不承诺其提供 getReader。此轮不宣称完整实现所有 Request/流上传/BYOB 等 Fetch 能力。

## 验证与范围

本地 22 项检查已通过，包括原版汽水读取器、跨块中文、克隆、不可重复消费、锁定、提前取消、等待正文时中止、空正文和旧 WebView 回退。新增安卓测试直接连接受控 HTTP 服务器，必须在 EOF 前收到字节前缀，并确认 cancel、abort 和 dispose 关闭真实连接；同时读取本地文件并调用固定指纹的原版汽水解析函数。登录过期和验证码错误必须原样保留，不能虚报认证成功。

这项修改修复通用网络 API，不代表已完成汽水真实用户登录、会员播放，或任意桌面辅助程序移植。原版声明的辅助程序仍须具备 Android 实现，平台账号与地区条件仍由服务端决定。

最终云端回归、签名、5.6/5.18 覆盖升级及交付文件校验待完成后补充。
