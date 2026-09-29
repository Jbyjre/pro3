# Rules for Claude sessions on this repo

## Standing rules from Jake (owner)
- NEVER auto-subscribe to PR or merge updates. Do not call `subscribe_pr_activity`,
  do not enable auto-merge, do not schedule check-ins or polling on PRs/CI, unless
  Jake explicitly asks for it in the current session.
- Jake has no coding experience: explain technical things in plain language, never
  hand him terminal commands to run. Do the repo/build/release work yourself.
- Verify facts before stating them. Mark anything unchecked as "not verified".
- Ask before spending money or doing anything hard to undo.

## Project notes
- Android app lives in `android/`. Build with `./gradlew assembleFossDebug` (or the
  `release-apk.yml` workflow, which signs with a keystore stored in repo secrets
  that this fork owns, never the upstream developer's keys).
- See `DECISIONS.md` and `TESTING.md` at the repo root.
