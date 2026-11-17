"""Hash-verified previous implementation as a test-only behavioral control."""
import hashlib, json, pathlib, re

root = pathlib.Path(__file__).resolve().parents[1]
original = (root/'tests/fixtures/audio-cache-5.47.java').read_bytes()
digest = hashlib.sha256(original).hexdigest()
assert digest == '20929f3dfac2a261094895c982d194248808bb83ecc8a3bcc0f750148cdd6ee4'
# The implementation remains intact; only the class identifier is renamed.
text = re.sub(r'\bAudioCache\b', 'BaselineAudioCache', original.decode())
(root/'app/src/androidTest/java/com/mygo/music/BaselineAudioCache.java').write_text(text)
(root/'app/src/androidTest/assets/cache-regression-proof.json').write_text(json.dumps({
    'baselineCommit':'d5903646b50f27e39e7397c0c62061164a6a105e',
    'baselineSha256':digest,
    'scope':'Only class identifier renamed; test APK only, excluded from production APK'},indent=2))
print('Verified 5.47 cache control:',digest)
