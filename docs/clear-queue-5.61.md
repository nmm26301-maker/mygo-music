# Queue clearing and resume, v5.61.0

The main and classic player queue now offers a clear action with confirmation tied to the displayed queue. If the queue changes during confirmation, the listener must confirm the updated queue.

Clearing the queue and removing its last entry now reset saved resume data, the player selection, and the current stream metadata before publishing the empty state. Ordinary stop still saves the selected entry and position for resume. Playlists, favorites, and cached files are retained.

The Android 13 instrumentation confirmed cancel and obsolete confirmation, clear with the queue open, last-entry removal, and an empty mini player with no saved position. The offline test stopped and unbound the service, relaunched the activity with a fresh service, and verified the cached rendition and seek position resumed. The full suite and signing migration passed.

- Tested commit 885ead450e5b557b9ba738b89f09672751af9645
- Full run https://github.com/nmm26301-maker/mygo-music/actions/runs/44753984299
- APK SHA-256 2c2bf5fb0afafd94b705c19660dd93ca2bdff669839d405fcff42d09c3c9151c
- Verified package com.mygo.music, SDK minimum 33, version code 111, the v5.60.0 publisher certificate, and identical APK bytes in the ZIP
