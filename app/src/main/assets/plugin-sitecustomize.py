"""Use the app-exported Android trust store for native Python HTTPS."""
import os
import ssl

_android_ca = os.environ.get("SSL_CERT_FILE")
if _android_ca:
    def _load_android_roots(context):
        # OpenSSL's default-path environment lookup can be suppressed for
        # Android app processes. Explicit loading preserves verification.
        context.load_verify_locations(cafile=_android_ca)
        if not context.get_ca_certs():
            raise ssl.SSLError("Android trust store contains no CA certificates")

    ssl.SSLContext.set_default_verify_paths = _load_android_roots
