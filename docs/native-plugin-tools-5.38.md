# Android native plugin tools 5.38

Continues the 5.37 player without replacing PlaybackService or its ten scenes.

Adds protocol 0.1.31 host support: instance-scoped Android Keystore secrets,
effective system proxy discovery, pinned native yt-dlp/Python/QuickJS, and an
MP3-capable FFmpeg helper. Native processes still require the source's declared
process permission and the user's grant; network extraction also requires the
network grant. The runtime ships inside the APK and is checksum checked.

The API 33 native test exposed zero default Python trust roots despite the
exported Android CA file. The Python adapter now explicitly loads that same
app-exported system trust store when an SSL context loads default certificates.
It preserves hostname checking and CERT_REQUIRED; it does not add third-party
trust anchors or disable certificate verification. Runtime cache identity is
advanced so existing extracted runtimes also receive the adapter.

Tests inspect both Python's default context and the actual pinned extractor's
SSL context, in addition to native JS, stdin, audio URL extraction, process
cancellation, and the unmodified original source's host diagnostics.

Final build status, source audit, external-service availability, signing and
upgrade evidence must come from a successful same-commit workflow. Host
compatibility does not establish paid-account access or regional availability.
