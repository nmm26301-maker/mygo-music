# Locate the current song, v5.62.0

The shared queue opens at the current entry and marks it with a background highlight and an accessible title. Playback updates move the marker without scrolling a queue that is already open. The Locate button scrolls to the latest current entry without changing playback. A current-index update reuses the queue snapshot until the revision changes.

The Android 13 instrumentation opened the real main activity queue with 24 entries, checked the marked row, browsed away, advanced to the next track, and located the new row with the same active player. Dragging, stale gestures, clearing and offline resume checks passed in the full run.

- Tested commit 2ad0589bfbe1de26fa78f6f9119772531bb2282c
- Full run https://github.com/nmm26301-maker/mygo-music/actions/runs/44762294926
- APK SHA-256 d38c8f5186282a0da8a7458d897e6ceb96234d5d56bbc1a60ae27b7c47372ef2
- Verified package com.mygo.music, SDK minimum 33, version code 112, the v5.61.0 publisher certificate, and identical APK bytes in the ZIP
