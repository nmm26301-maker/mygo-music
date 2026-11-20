# Main and classic player queue, v5.60.0

The main and classic player entry shared a separate list that retained positions after playback queue changes. Both now open the same live queue component used by the themes. The component refreshes on queue changes, validates row and menu actions, and lets listeners reorder or remove tracks without leaving the dialog. A library selection made before an active queue appears cannot replace that new queue.

The Android 13 checks open the actual MainActivity dialog, reorder the service queue, reject the old row, play the newly displayed track and see the list clear while the dialog remains open. The first full run passed those checks but timed out during PNG compression in the existing detached lyric rendering tests. The rendering and file output moved to a looper thread for detached test views. Real dialog controls are still exercised on the app thread. The full suite then passed with the same production code.

- Tested commit 920d3bcc435de13e81868916dd36774a4e3eecc5
- Full Android run https://github.com/nmm26301-maker/mygo-music/actions/runs/44746515982
- Release APK SHA-256 c4d8717a3043a9f63c9cdff0e58aa36607f2da6e0c8fe9a63c82a3b3fe76a9c2
- Verified Android 13 minimum, package com.mygo.music, version code 110, same publisher certificate as v5.59.0, and matching APK in the backup ZIP
