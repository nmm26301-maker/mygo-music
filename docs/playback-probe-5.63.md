# Cancel superseded playback probes, v5.63.0

A rapid skip could leave requests for old audio URLs occupying the media workers until their header reads timed out. The service now cancels the probe when the playback selection changes or stops. It disconnects on a worker and ignores callbacks from probes that no longer belong to the current playback selection. Redirect rules and origin-specific header handling stay in PluginMedia.

The Android 13 test withheld response headers from two skipped songs and watched both sockets close. The third song then played native audio. Stopping during a fourth probe also closed the socket without reporting a playback error. The full Android suite and signing migration check passed.

- Tested commit 6a8e99279e853cfe878cba5b4c337a5c4229fafa
- Full run https://github.com/nmm26301-maker/mygo-music/actions/runs/45759271740
- APK SHA-256 2fef857574429c71b4df06cd24a8c9457d34c45d19c81ad13112381a77290c61
- Verified package com.mygo.music, SDK minimum 33, version code 113, the v5.62.0 publisher certificate, and matching APK bytes in the ZIP
