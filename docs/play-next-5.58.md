# Explicit play-next priority, 5.58.0

The source-track action already inserted a requested song after the current row, but shuffle navigation could ignore that row and choose another entry or replay forward history. The existing playback service now keeps pending explicit requests by queue-entry identity and consumes them before ordinary next selection. Most-recent-first ordering matches the existing sequence-mode insertion position.

Requests survive pause/resume, playback-mode toggles and queue moves. Resolving an entry manually consumes that exact request. Removing an entry removes its request. Replacing or clearing the queue clears old requests. Remote state acknowledgements consume matching entries. After priority playback, shuffle retains its remaining cycle. Separate same-song entries, including offline cached qualities, retain their own identity.

Notification and media-session transport use the same service next operation. Sleep and single-track-repeat policy retain their existing precedence over automatic next.

Application commit: 08d233268f8b5de8372a851704d53f86f04ec1cb.

Actions: https://github.com/nmm26301-maker/mygo-music/actions/runs/44721792591 .

The 200-seed existing shuffle-order regression passed. The Android 13 targeted test emitted MYGO_PLAY_NEXT_OK for real notification PendingIntent and MediaController next commands, advancing native audio, uninterrupted enqueue, LIFO priority, pause/resume, forward-history override, remaining-cycle continuation, mode changes, reorder, manual consumption, removal, natural completion, sequence mode, replacement, clear and exact offline-quality selection. Targeted source playback and disposable-key signing rotation/data preservation also passed. The same-commit second attempt passed the full Android 13 suite, theme restart, public provider checks and 64-source staged audit. The native theme speed-clock measurement was 1.385917 and passed the existing unchanged 1.25–1.8 acceptance interval. Production checks verified the original package/version/SDK, same 5.57 signer and approved lineage, alignment, all four ABIs, the new production field, exclusion of test classes, corresponding-source equality and the byte-identical backup APK. Production signing checks are static; the final signed APK was not installed on a physical phone in this run.

The cover continues the delivered com.mygo.music package, versionCode 108, SDK 33 minimum and retained replacement signer 57cd10456757c347574fb62c5bae021e96c1f8e1ae78cc5b3eb857605f6a5bb8. Private signing recovery remains outside Git and the source archive.

Initial full-suite attempt: ThemeChecks accepted the native 1.5 playback parameter but its three short clock windows produced 1.2180097, 1.1380283 and 1.8607827; the existing median guard failed. Android AudioTrack logged retrograde timestamp corrections during the same interval. The same application commit passed on attempt 2 with unchanged speed code and assertion thresholds. The original failure and AudioTrack timestamp corrections remain in attempt-1 artifacts; successful full-suite evidence is in android-plugin-test-results-2.

APK SHA-256: 9cfcf80dd4522d3afb3539e01e6be1845bdb74c422ce8332f60cd566b978b9f9. Backup SHA-256: 1fd07ab0fafec7a6442361b1ec00d140789451f06e3a4a2591bc948feae70fe0.
