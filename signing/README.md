# Android release signing

The former signature is retired because its private material was exposed. Main-branch pushes run tests with disposable keys. Android 13 rotation tests install a disposable earlier build, apply the rotated update, and check that app data survives and that the earlier signer cannot install a subsequent update.

Create a protected GitHub Actions environment named `android-release`. Restrict deployments to the default branch. Add the new store and its password as the environment secrets `MYGO_RELEASE_KEYSTORE_B64` and `MYGO_RELEASE_STORE_PASSWORD`. Add `MYGO_RELEASE_KEY_PASSWORD` if it differs. Set the environment variables `MYGO_RELEASE_KEY_ALIAS` and `MYGO_RELEASE_CERT_SHA256` from the approved signer. Keep a protected backup of the new private key with the repository owner.

A manual run on main with `fresh_install_release` produces an APK for fresh installs. The signing step requires the pinned certificate and rejects the retired certificate. Keep that build separate from any upgrade for existing installations.

A production update of existing installations requires an approved signing lineage and a migration test using the delivered APK and representative app data. The disposable-key test does not attest to the production lineage. Test older Android versions separately before offering them an update.
