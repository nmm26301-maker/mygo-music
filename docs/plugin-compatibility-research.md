# Android plugin compatibility research, 2026-11-12

## Reference implementation

The active upstream host is [egoist/mygo-music](https://github.com/egoist/mygo-music). Its README documents the ES module API and `requestHeaders`; `internal/plugin/headers.go` and `source.go` provide the settings/storage and merge semantics. The upstream project is AGPL-3.0. Android implements the protocol in Java rather than embedding a second JS engine or copying an unrelated player.

The missing host behavior affected six inspected manifests: Guyu23 QQ Music and Qishui, InkCitrus Apple Music, LYJW131 Apple Music and Emby/Jellyfin, and yusgsysh FNOS. Fixed headers, dynamic server names, current storage values and per-media kinds are required. A source can return its own explicit audio headers, which take precedence regardless of letter case. Empty values and CR/LF are rejected. Tokens are kept out of URL query parameters and artwork cache keys are scoped to account headers.

[Android MediaPlayer](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/media/java/android/media/MediaPlayer.java) documents `android-allow-cross-domain-redirect`. [MediaHTTPConnection](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/media/java/android/media/MediaHTTPConnection.java) implements it. We resolve the initial redirect chain with the existing native HTTP stack, discard headers on scheme/host/port changes, and disable further cross-domain player redirects. No new network dependency is needed. A later same-host redirect to a different port remains an Android framework limitation, so tests explicitly cover the initial cross-port redirect chain.

## Approaches compared

| Approach | Dependencies | Relevant limitation | Decision |
| --- | --- | --- | --- |
| Upstream declarative media headers | Existing JSON, preferences, native HTTP | Must preserve domain restrictions and read current credentials on each request | Implement this documented protocol |
| Patch credentials into every plugin URL | Changes third-party packages | Token exposure, brittle provider-specific behavior | Reject |
| Add Babel or Sucrase for TypeScript | Extra parser/transform bundle | All 64 inspected current entries are JavaScript; 3 source archives instead require official built release ZIPs | No compiler added |
| Run downloaded desktop helper binaries | Platform-specific processes and native dependencies | macOS AppleScript, Windows Soloist, desktop ffmpeg/yt-dlp cannot be treated as Android executables | Report concrete setup/platform conditions |

## Installer identity

64 repositories include multiple unrelated publishers declaring the same id. Declared ids alone are insufficient for account isolation. Existing installations retain their current id; a new conflicting repository gets a deterministic repository suffix. Updates match repository and declared id. Both preferences and runtime permissions use the installed instance id, while plugin manifests are unchanged. Rejected updates preserve the previous installation and account.

## Test scope

`tests/market-pins.json` records commit, SHA-256, package URL, declared id, process declaration and root license-file presence for every catalog entry. Pinned author release ZIPs are used for AkinoKaede NetEase, LYJW131 Apple Music, and LYJW131 Emby/Jellyfin because their source archives omit `index.js`. Third-party code is fetched only into ignored test assets during CI; it is not added to production APK assets or this repository. Root license-file presence is a discovery aid, not an assertion of license compatibility or an authorization to copy code.

The market audit performs the real Android ZIP installation and module validation, then anonymous search, one stream resolution, an HTTP redirect probe and Android decoder preparation where possible. It neither logs into user accounts nor changes playlists/likes nor executes desktop helpers. Unavailable credentials, subscriptions, private servers, remote devices, geographic restrictions, rate limits and upstream errors remain explicit unverified conditions. Decoder preparation does not certify an entire music catalog, every export, or authenticated playback.

`PluginMediaChecks` exercises two real installations sharing a declared id, independent persistent tokens, repository update identity, rejected-update rollback, dynamic media headers, fresh token reads, case-insensitive stream precedence, domain checks, cross-origin redirect credential stripping, real protected cover rendering, and actual protected audio playback through the foreground service.
