# Latest checkpoint: Android 5.19.0 development, versionCode 69

Current work adds pull-based native response bodies for real plugins that call response.body.getReader(). Local 21 checks pass; the complete Android run, signing and upgrade checks are pending. The last delivered validated code remains 794fe3895b269050a01b8ec321c40cbede438565, Android 5.18.0, run 43371762017. See stream-body-5.19.md for current implementation, pinned original Qishui proof and verification boundaries. Resume from current main without reinitializing. The following migration map is historical context.

# Android migration checkpoint, 5.15.0

Baseline: egoist/mygo-music v0.1.24, commit 552e25c04023aa00c551d77db69aaab16f60bf2d, reviewed 2026-11-12. Android package remains com.mygo.music; versionCode 65. This is a resumable migration checkpoint, not a claim that every third-party service or desktop-only helper works on Android.

## Implementation map

| Upstream capability | Android implementation | Evidence / remaining condition |
| --- | --- | --- |
| Plugin ZIP / GitHub installation, discovery, enable/remove, atomic updates | PluginManager, PluginArchive, repository-specific identities, deep-link install review | Existing original-provider and 64 pinned repository Android audits; per-stage failures retained |
| ES modules, dynamic/relative imports, host APIs | PluginRuntime, plugin-host.js, PluginResources | Real Android WebView instrumentation; native network, cookies, processes and redirect callbacks |
| TypeScript entry and imported modules | Lazy offline Babel transforms, original URL and browser module graph retained | FullPortChecks: enum, type-only import, .js-to-.ts import, dynamic .ts import, unused invalid test ignored, invalid entry update rollback |
| Minimum app protocol version | PluginProtocol 0.1.24, separate from Android version 5.15.0 | Reject newer or malformed requirements before publishing plugin update |
| signIn / signOut / account / setSetting | Native prompts, web login, QR gallery save, private persisted source data, typed ordered settings | Existing QQ, Kugou, Subsonic and Spotify protocol checks; foreground login invalidates stale service runtime session |
| home / browse / search / suggest / recent / liked / isLiked / like | SourceBrowser, SourceSuggestions, MainActivity | Existing source UI tests plus debounced suggestions, stale response rejection; live authentication depends on provider |
| album / artist / savedAlbums / saveAlbum / followArtist | SourceBrowser and source navigation | Existing acknowledged save/follow UI tests; metadata and sized images retained |
| playlists / playlist / createPlaylist / renamePlaylist / deletePlaylist / savePlaylist / addToPlaylist / removeFromPlaylist / movePlaylistTrack | SourcePlaylists and track action menu | Existing editable playlist UI tests; source-track add chooser filters to owned editable lists |
| shows / show / subscribeShow / likeEpisode | SourceBrowser, showId metadata and episode-specific favorite dispatch | Existing show pagination/subscribe tests; unsupported episode favorites hidden; live account required for some providers |
| stations / stationNext | SourceBrowser plus PlaybackService radio context and bounded prefetch | New native audio test proves next batch c plays after a and b; duplicate batches and delayed replies cannot overwrite changed station |
| comments / likeComment / postComment | SourceComments | Raw offset pagination, ID dedup, provider-acknowledged likes and server-returned post; controlled fixture only, no live posting |
| stream / played / lyrics | Service-owned player, source headers, native audio cache, native lyric stage | Existing native background/notification/focus/cache and six lyric style tests; original and estimated timings remain distinguished |
| remotePlay / remotePause / remoteResume / remoteSeek / remoteVolume / remoteState / remoteDevices / remoteUse | PluginRemote and service media session | Original Spotify script against controlled authenticated protocol server; real account and active remote device needed |
| Queue controls and public sharing | Service queue append/next/move/remove/clear, source album/artist/show actions, original public track URL | Native player identity preserved during non-current edits; large queues use bounded app-private file handoff instead of Binder payload |
| SoundGo HLS and browser login | Exact-source-hash adapter in PluginAdapters, native HLS playback, existing token validation retained | Original source fixture must resolve and play actual HLS segments without spawning ffmpeg; live SoundCloud login remains unverified |

## Research and dependency choice

Reviewed maintained primary projects and official sources before implementation:

* [egoist/mygo-music](https://github.com/egoist/mygo-music), AGPL-3.0, active 2026-11-11: source interface, track/show/comment models, settings and protocol semantics are the reference. Host functionality is ported into the existing Android architecture rather than reinitializing the project.
* [babel/babel](https://github.com/babel/babel), MIT, active 2026-11-12: official @babel/standalone 7.29.13 is pinned and SHA-512 checked at build time. Compiler and full MIT license are bundled offline. Only requested TypeScript modules are compiled. Babel does not type-check, bundle npm dependencies, or provide Node built-ins.
* [alangpierce/sucrase](https://github.com/alangpierce/sucrase), MIT, last reviewed push 2025-11-19: smaller transforms but older maintenance and extra browser packaging; not added alongside Babel.
* [satyawikananda/soundgo](https://github.com/satyawikananda/soundgo), AGPL-3.0-only, reviewed commit 3a5b3e6bd2f6e5bb833be1f1a658a4e4095fc7fe: original selects unencrypted media, then uses ffmpeg for HLS. Android can play the authorized HLS URL directly. Installed source is retained byte for byte; an in-memory adaptation applies only to SHA-256 bd256059a8be720df5b79db6a8da90d7a0aba0d660fd4057afba8dac97c0196e. Unknown revisions are never rewritten.
* [androidx/media](https://github.com/androidx/media), Apache-2.0, and [official HLS documentation](https://developer.android.com/media/media3/exoplayer/hls): a full player replacement introduces wider lifecycle/cache regressions. This checkpoint keeps the proven native player and tests Android's actual HLS decoder with segmented AAC fixtures. No DRM handling is bypassed. Platform codec limitations remain.
* [Android transaction size guidance](https://developer.android.com/reference/android/os/TransactionTooLargeException): song metadata can exceed the shared Binder transaction limit; large queues are transferred via a validated private cache file, bounded at 8 MB and deleted after consumption.

## Validation and resumption

GitHub Actions is the required build path. It prepares pinned TypeScript/compiler and provider fixtures, runs the complete native instrumentation suite, audits 64 pinned market repositories, builds with the stable release signing certificate, and verifies overwrite upgrades from signed v5.6.0 and v5.12.0. A failed stage blocks release packaging. The FFmpeg build-host dependency is used only to generate test audio; the Android app does not bundle desktop FFmpeg.

FullPortChecks is the new first test stage so module, provider adapter or radio failures stop the run early. Existing lyrics, QR, process, login, playlist, network/header and background audio checks continue unchanged. Inspect the latest Actions run and its android-plugin-test-results artifact before claiming tests passed.

Explicit stop now releases the finished media session and creates a new identity. The foreground notification also gets a new ID: Android 13 SystemUI looks up the current controller by notification key when asynchronously dismissing old media, so reusing that key can stop a new session. Session callbacks and notification controls carry the session identity so delayed controls from a stopped song cannot stop a newly started queue. Android regression tests send both the old MediaController stop and the old notification PendingIntent after a new radio has started. This follows the [official MediaSession lifecycle](https://developer.android.com/reference/android/media/session/MediaSession).

## Remaining work requiring explicit compatibility proof

* Live credentials, memberships, remote devices and user-owned servers cannot be inferred from successful module installation. See the shipped per-source diagnostic report for the exact tested stage.
* macOS AppleScript, Windows desktop applications and downloaded desktop executables need Android-specific replacements or an explicitly configured external device. Declared process permission is not a compatibility guarantee.
* ReplayGain gain/peak, global settings ordering and remote completion-driven station continuation passed Android CI in commit 7efe7d72ff65c30d7a1db88a5430f8c5a91e1625, run 43227538227. ALAC and complete HLS offline conversion are implemented in the next checkpoint; final native decoder regression evidence is recorded in the 5.15 release report. Codec parity does not imply DRM support or compatibility with arbitrary desktop FFmpeg arguments.
* Cross-manager setting acknowledgement and rollback are now globally ordered and tested with simultaneously open native dialogs. Account-specific live source tests still require real user sessions.
* Revalidate any newer SoundGo revision against its license and changed implementation before adding a new source hash. Do not turn off hash checks to force compatibility.

Resume from GitHub main and the latest workflow logs. Do not reinitialize or repeat successful migration stages. Preserve existing users' package identity, signing key, source credentials, preferences and local music database.

## Native audio completion checkpoint, 5.15

The existing application and media service are retained. PlaybackLoudness applies source gain plus the upstream 4 dB preamp, limits by peak, and uses the actual Android audio session for amplification. SourceSettingQueue serializes the whole preference/acknowledgement/rollback transaction across managers. RemoteCompletion distinguishes manual pause from natural track completion before advancing a station.

FFmpeg 9.0.2 was selected after comparing maintained FFmpeg/FFmpeg, AndroidX Media3 and the retired arthenica/ffmpeg-kit wrapper. Official source: https://ffmpeg.org/releases/ffmpeg-9.0.2.tar.xz . SHA-256: 8c3850283eb25fa026482078a04051e0be17347b09ef81a0849bec15a96e002e. Its detached release signature was verified against the official FFmpeg signing key FCF986EA15E6E293A5644F10B4322F04D67658D8. See https://ffmpeg.org/download.html and https://ffmpeg.org/legal.html . No binaries from unmaintained wrappers are used.

The separately executable helper is compiled for arm64-v8a, armeabi-v7a, x86_64 and x86 with NDK 27.2.12479018. GPL, nonfree, version3, autodetected external libraries and desktop protocols are disabled. Full configure commands, LGPL text and credits are shipped. The corresponding-source ZIP contains the unchanged pinned FFmpeg tarball and Android app/build sources, excluding signing keys. Original HTTPS and authorization remain in Java; only a random-token loopback URL is passed to the helper. Cross-origin requests do not inherit another origin's explicit headers; child-specific manifest requestHeaders are evaluated independently.

NativeAudioChecks exercises actual Android executable conversion of 16/24-bit ALAC, lossless FLAC stream format, native playback/pause/seek/resume, source metadata and played acknowledgement, master and AES-128 HLS offline files after stopping the original server, segment/key headers, cross-origin credential separation, live/DRM rejection and cache-clear cancellation. It is a controlled transport/codec check, not a substitute for a subscribed third-party account.

HLS offline caching supports finite VOD manifests, standard AES-128 keys and audio streams accepted by the pinned decoder. Live manifests, DRM and EXT-X-DEFINE variables produce an explicit cache error and do not publish partial songs; ordinary supported online playback remains available. The output is complete FLAC, not a saved expiring manifest. Unsupported ALAC/M4A is decoded into a complete local FLAC before native playback. This is bounded by 128 MiB per song and a 180-second conversion timeout. It does not provide general-purpose yt-dlp, macOS applications, Windows programs, Node built-ins or arbitrary FFmpeg process compatibility.

## Final 5.15 validation and resume point

Final tested code: db07b7b901ee52209810663083e90fcb2e46bed6. GitHub Actions run 43231960894 completed successfully: https://github.com/nmm26301-maker/mygo-music/actions/runs/43231960894 . NativeAudioChecks emitted MYGO_NATIVE_AUDIO_OK at 2026-11-12 14:00:36 UTC; the complete instrumentation and 64-source audit passed. Signed 5.6.0 and 5.14.0 both upgraded to 5.15.0 / versionCode 65 on Android 13 without uninstalling in the upgrade step. Runtime coverage is x86_64; all four Android ABIs are compiled and packaged.

Final anonymous audit: 64 pinned packages attempted, 63 installed and loaded, 42 search calls passed (including valid empty results), 11 HTTP audio responses passed, 9 native decoders prepared. These stages are not a certification that all sources or tracks play. maxbeau/mygo-music-youtube-music still requires protocol 0.1.31 and desktop tooling; no version guard was bypassed. Other account, private-server and external device conditions remain itemized in assets/plugin-audit.json. Live user credentials were not fabricated or used.

The delivered APK was checked again for stable certificate, package/version, actual ELF architecture for all four audio helpers, LGPL/AGPL notices, absence of test classes, the bundled 64-source audit, exact equality to the APK inside the install ZIP, and complete checksum-pinned FFmpeg source without release signing keys. APK SHA-256: b106aaed8b49ba8e4b7b03d138d0c7eb38559ddda39bc604133b629cbffa6207. Source ZIP SHA-256: 0e63b39498e3912fa9eb8d1babd5829b2b05baf90b9c4da79fc98f2f1b268f4f.

See release-5.15.md for installation, exact validated scope and artifact checksums. Resume from current GitHub main; this final documentation commit does not change the tested application code. Remaining work requires a concrete source/API/OS replacement, user-supplied account/device conditions or a newly supported protocol. Do not reimplement verified features or claim the listed external conditions have been completed.


## QQ camera-only authorization checkpoint, 5.17

The user's new platform message explicitly forbids gallery/long-press QR authorization for this request. The 5.16 refresh-and-save path cannot satisfy a camera-only request. Hash-pinned QQ source now defaults to the official QQ Music website; only a music credential validated by the original account endpoint is persisted. Web credentials use the web request context and CgiGetVkey route. Native OAuth credentials keep their original route. This does not spoof camera capture or guarantee that the official website offers a password path for every account.

Validated code 4ad7a70f4556f8bbb9cc8712242622c74cf06cdf, successful Actions run 43350969606. All 14 Node checks, full Android instrumentation, 64-source anonymous audit, stable release signature, signed overwrite upgrades from 5.6.0 and 5.16.0, and delivered-file checks passed. The official-website fixture tests actual pinned QQ source, candidate validation before save, original web vkey to native playback, cancellation/failure persistence, cookie isolation, a usable portrait viewport and landscape restoration. The fixture is not a real user's account or proof of membership access. Anonymous live QQ search still returned 0/2001; concept-edition stream required sign-in.

63 sources installed/loaded, 43 search calls passed, 10 audio HTTP responses passed and 8 Android decoders prepared; per-source limits remain in the APK audit. Existing migration boundaries remain. See qq-official-login.md for exact behavior, build evidence and checksums. Resume from current main without reinitializing; the final documentation-only commit does not alter the tested application code.


## QR accessibility, source login and landscape controls, 5.18

Validated application code 794fe3895b269050a01b8ec321c40cbede438565; successful Actions run 43371762017. Native QR codes appear before long instructions and have a full-screen view; website QR images can also be regenerated at a readable size without saving or completing login. Save/open actions are checked for full visibility. Browser navigation invalidates delayed image actions. Ximalaya's official scanner package is now visible and routed correctly. Website login uses a larger portrait panel and restores the player orientation.

The pinned MIT Bilibili source uses its existing native QR generation and polling on Android, preserves original files, and validates the candidate session before replacing the saved account. Failed login preserves the prior account. Unknown source revisions are not rewritten. The current source account action remains visible independently of content loading; configuration-only sources open settings rather than calling a missing signIn export. Landscape navigation, previous/next and favorite controls use local vector drawing with accessible labels.

The full Android suite, 14 Node checks, anonymous 64-source audit, stable signature, overwrite upgrades from 5.6.0 and 5.17.0 and delivered-file checks passed. The anonymous audit recorded 63 installs/loads, 39 search calls, 8 audio HTTP responses and 6 prepared decoders; these are separate stages, not certification of every plugin or account. Real credentials, subscriptions, regional access, captchas and previously documented desktop/protocol conditions remain outside controlled verification. See login-qr-5.18.md for source/license comparison, exact checks and artifact hashes. Final documentation commits leave the tested application code unchanged.
