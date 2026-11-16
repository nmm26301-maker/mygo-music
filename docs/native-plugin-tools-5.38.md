# Android native plugin tools 5.38

Continues the 5.37 player without replacing PlaybackService or its ten scenes.

Adds protocol 0.1.31 host support: instance-scoped Android Keystore secrets,
effective system proxy discovery, pinned native yt-dlp/Python/QuickJS, and an
MP3-capable FFmpeg helper. Native processes still require the source's declared
process permission and the user's grant; network extraction also requires the
network grant. The runtime ships inside the APK and is checksum checked.

The API 33 test exposed zero default Python trust roots. Explicit loading then
exposed an X509 PEM parsing error. A generated certificate whose base64 length
is an exact multiple of 64 reproduced the failure: the old regex writer appended
a second blank line before END CERTIFICATE. OpenSSL rejected that PEM.

The exporter now writes canonical 64-character lines with exactly one newline
and checks the complete exported certificate count before activating a runtime.
Its cache identity is advanced so an existing extracted runtime is rebuilt.
Python and yt-dlp use their ordinary SSL implementations and the corrected
app-exported Android CA file. No SSL monkey patch is shipped, and hostname
checking and CERT_REQUIRED remain enabled.

Tests inspect Python's default context and the actual pinned extractor's SSL
context, in addition to native JS, stdin, audio URL extraction, cancellation,
and the unmodified original source's host diagnostics. The local TLS check
rejects an untrusted certificate and accepts an explicitly trusted certificate.
A local certificate boundary fixture reproduces the old PEM error and confirms
the canonical export parses.

Final build status, source audit, external-service availability, signing and
upgrade evidence must come from a successful same-commit workflow. Host
compatibility does not establish paid-account access or regional availability.
