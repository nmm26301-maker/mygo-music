# Queue control lifetime, 5.59.0

A row listener previously retained its position but validated that position against the adapter's mutable current snapshot. After refresh, an old listener could therefore operate on the new item at that position. URI-only checks also could not distinguish duplicate songs or cached qualities. Menu and drag guards based only on JSON could accept an obsolete operation after an identical queue replacement.

Each rendered row now retains its position, queue revision and complete entry fingerprint. Text clicks, list clicks, remove buttons, context menus and drag starts use that binding. A changed queue invalidates the callback, refreshes the list and asks for a new selection. Menus additionally retain their full snapshot. Drops retain their owning widget, queue revision and snapshot so an identical replacement still rejects an earlier gesture. Index-only playback changes do not invalidate a row whose queue entry is unchanged. Pending adapter rows with no entry do not dereference null metadata.

QueueControlChecks uses the actual MainActivity host, service, native row controls, popup windows, audio clocks and physical drag gestures. It covers old callbacks after adapter refresh, valid playback and removal, index-only changes, identical-JSON menu replacement, normal menu sorting, same-URI offline quality selection, cleared rows, an obsolete drag after queue replacement and a subsequent valid drag without restarting the player.

The initial menu check found a visible popup but its generic accessibility click did not activate the item. The updated check observes popup readiness from the instrumentation thread and uses touch events at its visible menu bounds, allowing UI dispatch to proceed during observation. The production change is unchanged by that test-driver adjustment.

Application/test commit: 461415c2e561c1f5fc0f54908f1900590ee54d57.

Actions: https://github.com/nmm26301-maker/mygo-music/actions/runs/44730153845 .

The 200-seed existing shuffle-order regression passed. The targeted Android 13 run emitted MYGO_QUEUE_CONTROLS_OK for the complete native row, menu, cached-quality and physical-drag checks. Existing targeted playback and disposable-key rotation/data preservation also passed. The full Android 13 suite completed with MYGO_TESTS_OK, and its log independently records MYGO_QUEUE_CONTROLS_OK. The signed release passed Android package, version, signature lineage, alignment and ABI checks. The APK inside the backup ZIP matches the standalone APK byte for byte. The downloaded corresponding source archive matches the changed application and test files.

The release retains package com.mygo.music, versionCode 109, SDK 33 minimum and certificate SHA-256 57cd10456757c347574fb62c5bae021e96c1f8e1ae78cc5b3eb857605f6a5bb8. Private signing recovery remains separate from Git and corresponding sources.
