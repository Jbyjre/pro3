`update-valid.apk` is a minimal, code-free APK fixture: package io.github.jbyjre.glint,
versionCode 901, versionName 1.0.0, minSdk 33, targetSdk 37. Its only purpose is to exercise
the real APK cryptographic verifier and downgrade/signer checks. It contains no activities,
permissions, user data or production signing material. Its disposable test certificate is
not the release identity and must never be accepted as a production upgrade.
