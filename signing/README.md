# Android release signing

The former signature is retired because its private material was exposed. Main-branch pushes run tests with disposable keys. Android 13 rotation tests install a disposable earlier build, apply the rotated update, and check that app data survives and that the earlier signer cannot install a subsequent update.

Create a protected GitHub Actions environment named `android-release`. Restrict deployments to the default branch. Add the new store and its password as the environment secrets `MYGO_RELEASE_KEYSTORE_B64` and `MYGO_RELEASE_STORE_PASSWORD`. Add `MYGO_RELEASE_KEY_PASSWORD` if it differs. Set the environment variables `MYGO_RELEASE_KEY_ALIAS` and `MYGO_RELEASE_CERT_SHA256` from the approved signer. Keep a protected backup of the new private key with the repository owner.

A manual run on main with `fresh_install_release` produces an APK for fresh installs. The signing step requires the pinned certificate and rejects the retired certificate. Keep that build separate from any upgrade for existing installations.

For an existing installation on Android 13 or newer, use the `android13_upgrade_release` option. Provide the approved proof of rotation as `MYGO_RELEASE_LINEAGE_B64` in the protected release environment. Production CI uses only the new private key. The proof must link the delivered 5.53.0 certificate to the pinned replacement and allow the previous signer only to retain installed data. The workflow publishes the upgrade only after verifying the delivered baseline checksum, installing the new APK over that baseline on Android 13, and checking that seeded local data survives.

The normal workflow tests the same upgrade-signing path with two one-use keys. Neither production signatures nor a physical phone are represented by that test. The fresh-install mode supports the general app minimum SDK. The Android 13 upgrade mode enforces SDK 33 in its manifest.
