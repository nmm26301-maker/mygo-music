"""Validate the two signers and the exact capabilities of a retired APK signer."""
import re
import sys

old, new = sys.argv[1:]
report = sys.stdin.read()
certs = re.findall(r"(?m)^.* certificate SHA-256 digest: ([0-9a-fA-F]{64})$", report)
if [item.lower() for item in certs] != [old, new] or old == new:
    raise SystemExit("Expected exactly the approved original and new signing certificates")
capabilities = re.findall(r"(?m)^Has (.*?) capability\s*:\s*(true|false)$", report)
retired = dict(capabilities[:5])
if retired != {
    "installed data": "true", "shared UID": "false", "permission": "false", "rollback": "false", "auth": "false"
}:
    raise SystemExit("The retired signer must have installed-data trust only")
