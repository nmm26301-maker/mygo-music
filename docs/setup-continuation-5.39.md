# Plugin setup continuation 5.39

When playback or a quality change returned a source setup error, opening settings
discarded the operation's retry callback. After granting a helper permission or
entering configuration, users had to select the song again. Settings opened from
that error now offer Save and retry. Normal settings retain their Save button.

Continuation runs once after every setting in that dialog has been acknowledged.
A failed write rolls back and leaves the dialog open; closing or dismissing the
dialog cancels continuation even if an outstanding write later succeeds. All
fields are submitted before synchronous callbacks may complete the save.

Playback and quality changes reuse the existing service retry guards. A newer
queue or same-song session, a changed quality request, disabled source, or stopped
session cannot be replaced by the old settings window. Network-error permission
settings use the same continuation route when the caller supplied a retry.

SetupRetryChecks exercises the real error/settings UI, a source acknowledgement,
the packaged native yt-dlp process, advancing native WAV playback, retained queue,
index, position, modes, volume and quality, close-during-save, rejection/rollback,
same-song replacement, and paused quality recovery. Original provider availability
and membership entitlement remain independent of this host-side recovery path.

The corresponding source archive now includes the release-generated source audit
alongside native runtime metadata, matching the APK without a local packaging fix.

Final validation must use the successful 5.39 workflow, not an earlier build.
